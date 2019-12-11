/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.util.resource;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.protocolrecords.ResourceTypes;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceInformation;
import org.apache.hadoop.yarn.api.records.ResourceTypeInfo;
import org.apache.hadoop.yarn.conf.ConfigurationProvider;
import org.apache.hadoop.yarn.conf.ConfigurationProviderFactory;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.hadoop.yarn.api.records.ResourceInformation.GPU_URI;

/**
 * Helper class to read the resource-types to be supported by the system.
 */
public class ResourceUtils {

  public static final String UNITS = ".units";
  public static final String TYPE = ".type";
  public static final String MINIMUM_ALLOCATION = ".minimum-allocation";
  public static final String MAXIMUM_ALLOCATION = ".maximum-allocation";

  private static final String MEMORY = ResourceInformation.MEMORY_MB.getName();
  private static final String VCORES = ResourceInformation.VCORES.getName();
  private static final Pattern RESOURCE_REQUEST_VALUE_PATTERN =
      Pattern.compile("^([0-9]+) ?([a-zA-Z]*)$");

  private static volatile boolean initializedResources = false;
  private static final Map<String, Integer> RESOURCE_NAME_TO_INDEX =
      new ConcurrentHashMap<String, Integer>();
  private static volatile Map<String, ResourceInformation> resourceTypes;
  private static volatile ResourceInformation[] resourceTypesArray;
  private static volatile boolean initializedNodeResources = false;
  private static volatile Map<String, ResourceInformation> readOnlyNodeResources;
  private static volatile int numKnownResourceTypes = -1;

  static final Log LOG = LogFactory.getLog(ResourceUtils.class);

  private ResourceUtils() {
  }

  private static void checkMandatoryResources(
      Map<String, ResourceInformation> resourceInformationMap)
      throws YarnRuntimeException {
    /*
     * Supporting 'memory' also as invalid resource name, in addition to
     * 'MEMORY' for historical reasons
     */
    String key = "memory";
    if (resourceInformationMap.containsKey(key)) {
      LOG.warn(
          "Attempt to define resource '" + key + "', but it is not allowed.");
      throw new YarnRuntimeException(
          "Attempt to re-define mandatory resource '" + key + "'.");
    }

    for (Map.Entry<String, ResourceInformation> mandatoryResourceEntry :
        ResourceInformation.MANDATORY_RESOURCES.entrySet()) {
      key = mandatoryResourceEntry.getKey();
      ResourceInformation mandatoryRI = mandatoryResourceEntry.getValue();

      ResourceInformation newDefinedRI = resourceInformationMap.get(key);
      if (newDefinedRI != null) {
        String expectedUnit = mandatoryRI.getUnits();
        ResourceTypes expectedType = mandatoryRI.getResourceType();
        String actualUnit = newDefinedRI.getUnits();
        ResourceTypes actualType = newDefinedRI.getResourceType();

        if (!expectedUnit.equals(actualUnit) || !expectedType.equals(
            actualType)) {
          throw new YarnRuntimeException("Defined mandatory resource type="
              + key + " inside resource-types.xml, however its type or "
              + "unit is conflict to mandatory resource types, expected type="
              + expectedType + ", unit=" + expectedUnit + "; actual type="
              + actualType + " actual unit=" + actualUnit);
        }
      }
    }
  }

  private static void addMandatoryResources(
      Map<String, ResourceInformation> res) {
    ResourceInformation ri;
    if (!res.containsKey(MEMORY)) {
      LOG.info("Adding resource type - name = " + MEMORY + ", units = "
          + ResourceInformation.MEMORY_MB.getUnits() + ", type = "
          + ResourceTypes.COUNTABLE);
      ri = ResourceInformation
          .newInstance(MEMORY,
              ResourceInformation.MEMORY_MB.getUnits());
      res.put(MEMORY, ri);
    }
    if (!res.containsKey(VCORES)) {
      LOG.info("Adding resource type - name = " + VCORES + ", units = , type = "
          + ResourceTypes.COUNTABLE);
      ri =
          ResourceInformation.newInstance(VCORES);
      res.put(VCORES, ri);
    }
  }

  private static void setMinimumAllocationForMandatoryResources(
      Map<String, ResourceInformation> res, Configuration conf) {
    String[][] resourceTypesKeys = {
        {ResourceInformation.MEMORY_MB.getName(),
            YarnConfiguration.RM_SCHEDULER_MINIMUM_ALLOCATION_MB,
            String.valueOf(
                YarnConfiguration.DEFAULT_RM_SCHEDULER_MINIMUM_ALLOCATION_MB),
            ResourceInformation.MEMORY_MB.getName()},
        {ResourceInformation.VCORES.getName(),
            YarnConfiguration.RM_SCHEDULER_MINIMUM_ALLOCATION_VCORES,
            String.valueOf(
                YarnConfiguration.DEFAULT_RM_SCHEDULER_MINIMUM_ALLOCATION_VCORES),
            ResourceInformation.VCORES.getName()}};
    for (String[] arr : resourceTypesKeys) {
      String resourceTypesKey =
          YarnConfiguration.RESOURCE_TYPES + "." + arr[0] + MINIMUM_ALLOCATION;
      long minimumResourceTypes = conf.getLong(resourceTypesKey, -1);
      long minimumConf = conf.getLong(arr[1], -1);
      long minimum;
      if (minimumResourceTypes != -1) {
        minimum = minimumResourceTypes;
        if (minimumConf != -1) {
          LOG.warn("Using minimum allocation for memory specified in "
              + "resource-types config file with key "
              + minimumResourceTypes + ", ignoring minimum specified using "
              + arr[1]);
        }
      } else {
        minimum = conf.getLong(arr[1], Long.parseLong(arr[2]));
      }
      ResourceInformation ri = res.get(arr[3]);
      ri.setMinimumAllocation(minimum);
    }
  }

  private static void setMaximumAllocationForMandatoryResources(
      Map<String, ResourceInformation> res, Configuration conf) {
    String[][] resourceTypesKeys = {
        {ResourceInformation.MEMORY_MB.getName(),
            YarnConfiguration.RM_SCHEDULER_MAXIMUM_ALLOCATION_MB,
            String.valueOf(
                YarnConfiguration.DEFAULT_RM_SCHEDULER_MAXIMUM_ALLOCATION_MB),
            ResourceInformation.MEMORY_MB.getName()},
        {ResourceInformation.VCORES.getName(),
            YarnConfiguration.RM_SCHEDULER_MAXIMUM_ALLOCATION_VCORES,
            String.valueOf(
                YarnConfiguration.DEFAULT_RM_SCHEDULER_MAXIMUM_ALLOCATION_VCORES),
            ResourceInformation.VCORES.getName()}};
    for (String[] arr : resourceTypesKeys) {
      String resourceTypesKey =
          YarnConfiguration.RESOURCE_TYPES + "." + arr[0] + MAXIMUM_ALLOCATION;
      long maximumResourceTypes = conf.getLong(resourceTypesKey, -1);
      long maximumConf = conf.getLong(arr[1], -1);
      long maximum;
      if (maximumResourceTypes != -1) {
        maximum = maximumResourceTypes;
        if (maximumConf != -1) {
          LOG.warn("Using maximum allocation for memory specified in "
              + "resource-types config file with key "
              + maximumResourceTypes + ", ignoring maximum specified using "
              + arr[1]);
        }
      } else {
        maximum = conf.getLong(arr[1], Long.parseLong(arr[2]));
      }
      ResourceInformation ri = res.get(arr[3]);
      ri.setMaximumAllocation(maximum);
    }
  }

  /**
   * Get maximum allocation from config, *THIS WILL NOT UPDATE INTERNAL DATA*
   * @param conf config
   * @return maximum allocation
   */
  public static Resource fetchMaximumAllocationFromConfig(Configuration conf) {
    Map<String, ResourceInformation> resourceInformationMap =
        getResourceInformationMapFromConfig(conf);
    Resource ret = Resource.newInstance(0, 0);
    for (ResourceInformation entry : resourceInformationMap.values()) {
      ret.setResourceValue(entry.getName(), entry.getMaximumAllocation());
    }
    return ret;
  }

  private static Map<String, ResourceInformation> getResourceInformationMapFromConfig(
      Configuration conf) {
    Map<String, ResourceInformation> resourceInformationMap = new HashMap<>();
    String[] resourceNames = conf.getStrings(YarnConfiguration.RESOURCE_TYPES);

    if (resourceNames != null && resourceNames.length != 0) {
      for (String resourceName : resourceNames) {
        String resourceUnits = conf.get(
            YarnConfiguration.RESOURCE_TYPES + "." + resourceName + UNITS, "");
        String resourceTypeName = conf.get(
            YarnConfiguration.RESOURCE_TYPES + "." + resourceName + TYPE,
            ResourceTypes.COUNTABLE.toString());
        Long minimumAllocation = conf.getLong(
            YarnConfiguration.RESOURCE_TYPES + "." + resourceName
                + MINIMUM_ALLOCATION, 0L);
        Long maximumAllocation = conf.getLong(
            YarnConfiguration.RESOURCE_TYPES + "." + resourceName
                + MAXIMUM_ALLOCATION, Long.MAX_VALUE);
        if (resourceName == null || resourceName.isEmpty()
            || resourceUnits == null || resourceTypeName == null) {
          throw new YarnRuntimeException(
              "Incomplete configuration for resource type '" + resourceName
                  + "'. One of name, units or type is configured incorrectly.");
        }
        ResourceTypes resourceType = ResourceTypes.valueOf(resourceTypeName);
        LOG.info("Adding resource type - name = " + resourceName + ", units = "
            + resourceUnits + ", type = " + resourceTypeName);
        if (resourceInformationMap.containsKey(resourceName)) {
          throw new YarnRuntimeException(
              "Error in config, key '" + resourceName + "' specified twice");
        }
        resourceInformationMap.put(resourceName, ResourceInformation
            .newInstance(resourceName, resourceUnits, 0L, resourceType,
                minimumAllocation, maximumAllocation));
      }
    }

    checkMandatoryResources(resourceInformationMap);
    addMandatoryResources(resourceInformationMap);

    setMinimumAllocationForMandatoryResources(resourceInformationMap, conf);
    setMaximumAllocationForMandatoryResources(resourceInformationMap, conf);

    return resourceInformationMap;
  }

  @VisibleForTesting
  static void initializeResourcesMap(Configuration conf) {
    Map<String, ResourceInformation> resourceInformationMap =
        getResourceInformationMapFromConfig(conf);
    initializeResourcesFromResourceInformationMap(resourceInformationMap);
  }

  /**
   * This method is visible for testing, unit test can construct a
   * resourceInformationMap and pass it to this method to initialize multiple resources.
   * @param resourceInformationMap constructed resource information map.
   */
  @VisibleForTesting
  public static void initializeResourcesFromResourceInformationMap(
      Map<String, ResourceInformation> resourceInformationMap) {
    resourceTypes = Collections.unmodifiableMap(resourceInformationMap);
    updateKnownResources();
    updateResourceTypeIndex();
    initializedResources = true;
    numKnownResourceTypes = resourceTypes.size();
  }

  private static void updateKnownResources() {
    // Update resource names.
    resourceTypesArray = new ResourceInformation[resourceTypes.size()];

    int index = 2;
    for (ResourceInformation resInfo : resourceTypes.values()) {
      if (resInfo.getName().equals(MEMORY)) {
        resourceTypesArray[0] = ResourceInformation
            .newInstance(resourceTypes.get(MEMORY));
      } else if (resInfo.getName().equals(VCORES)) {
        resourceTypesArray[1] = ResourceInformation
            .newInstance(resourceTypes.get(VCORES));
      } else {
        resourceTypesArray[index] = ResourceInformation.newInstance(resInfo);
        index++;
      }
    }
  }

  private static void updateResourceTypeIndex() {
    RESOURCE_NAME_TO_INDEX.clear();

    for (int index = 0; index < resourceTypesArray.length; index++) {
      ResourceInformation resInfo = resourceTypesArray[index];
      RESOURCE_NAME_TO_INDEX.put(resInfo.getName(), index);
    }
  }

  /**
   * Get associate index of resource types such memory, cpu etc.
   * This could help to access each resource types in a resource faster.
   * @return Index map for all Resource Types.
   */
  public static Map<String, Integer> getResourceTypeIndex() {
    return RESOURCE_NAME_TO_INDEX;
  }

  /**
   * Get the resource types to be supported by the system.
   * @return A map of the resource name to a ResouceInformation object
   *         which contains details such as the unit.
   */
  public static Map<String, ResourceInformation> getResourceTypes() {
    return getResourceTypes(null,
        YarnConfiguration.RESOURCE_TYPES_CONFIGURATION_FILE);
  }

  public static ResourceInformation[] getResourceTypesArray() {
    initializeResourceTypesIfNeeded(null,
        YarnConfiguration.RESOURCE_TYPES_CONFIGURATION_FILE);
    return resourceTypesArray;
  }

  public static int getNumberOfKnownResourceTypes() {
    if (numKnownResourceTypes < 0) {
      initializeResourceTypesIfNeeded(null,
          YarnConfiguration.RESOURCE_TYPES_CONFIGURATION_FILE);
    }
    return numKnownResourceTypes;
  }

  private static Map<String, ResourceInformation> getResourceTypes(
      Configuration conf) {
    return getResourceTypes(conf,
        YarnConfiguration.RESOURCE_TYPES_CONFIGURATION_FILE);
  }

  private static void initializeResourceTypesIfNeeded(Configuration conf,
      String resourceFile) {
    if (!initializedResources) {
      synchronized (ResourceUtils.class) {
        if (!initializedResources) {
          Configuration resConf = conf;

          if (resConf == null) {
            resConf = new YarnConfiguration();
          }

          addResourcesFileToConf(resourceFile, resConf);
          initializeResourcesMap(resConf);
        }
      }
    }
    numKnownResourceTypes = resourceTypes.size();
  }

  private static Map<String, ResourceInformation> getResourceTypes(
      Configuration conf, String resourceFile) {
    initializeResourceTypesIfNeeded(conf, resourceFile);
    return resourceTypes;
  }

  private static InputStream getConfInputStream(String resourceFile,
      Configuration conf) throws IOException, YarnException {

    ConfigurationProvider provider =
        ConfigurationProviderFactory.getConfigurationProvider(conf);
    try {
      provider.init(conf);
    } catch (Exception e) {
      throw new IOException(e);
    }

    InputStream ris = provider.getConfigurationInputStream(conf, resourceFile);
    if (ris == null) {
      if (conf.getResource(resourceFile) == null) {
        throw new FileNotFoundException("Unable to find " + resourceFile);
      }
      throw new IOException(
          "Unable to open resource types file '" + resourceFile
              + "'. Using provider " + provider);
    }
    return ris;
  }

  private static void addResourcesFileToConf(String resourceFile,
      Configuration conf) {
    try {
      InputStream ris = getConfInputStream(resourceFile, conf);
      LOG.debug("Found " + resourceFile + ", adding to configuration");
      conf.addResource(ris);
    } catch (FileNotFoundException fe) {
      LOG.info("Unable to find '" + resourceFile + "'.");
    } catch (IOException | YarnException ex) {
      LOG.fatal("Exception trying to read resource types configuration '"
          + resourceFile + "'.", ex);
      throw new YarnRuntimeException(ex);
    }
  }

  @VisibleForTesting
  synchronized static void resetResourceTypes() {
    initializedResources = false;
  }

  @VisibleForTesting
  public static Map<String, ResourceInformation>
      resetResourceTypes(Configuration conf) {
    synchronized (ResourceUtils.class) {
      initializedResources = false;
    }
    return getResourceTypes(conf);
  }

  public static String getUnits(String resourceValue) {
    String units;
    for (int i = 0; i < resourceValue.length(); i++) {
      if (Character.isAlphabetic(resourceValue.charAt(i))) {
        units = resourceValue.substring(i);
        if (StringUtils.isAlpha(units)) {
          return units;
        }
      }
    }
    return "";
  }

  /**
   * Function to get the resources for a node. This function will look at the
   * file {@link YarnConfiguration#NODE_RESOURCES_CONFIGURATION_FILE} to
   * determine the node resources.
   *
   * @param conf configuration file
   * @return a map to resource name to the ResourceInformation object. The map
   * is guaranteed to have entries for memory and vcores
   */
  public static Map<String, ResourceInformation> getNodeResourceInformation(
      Configuration conf) {
    if (!initializedNodeResources) {
      synchronized (ResourceUtils.class) {
        if (!initializedNodeResources) {
          Map<String, ResourceInformation> nodeResources = initializeNodeResourceInformation(
              conf);
          addMandatoryResources(nodeResources);
          checkMandatoryResources(nodeResources);
          setMinimumAllocationForMandatoryResources(nodeResources, conf);
          setMaximumAllocationForMandatoryResources(nodeResources, conf);
          readOnlyNodeResources = Collections.unmodifiableMap(nodeResources);
          initializedNodeResources = true;
        }
      }
    }
    return readOnlyNodeResources;
  }

  private static Map<String, ResourceInformation> initializeNodeResourceInformation(
      Configuration conf) {
    Map<String, ResourceInformation> nodeResources = new HashMap<>();

    addResourcesFileToConf(YarnConfiguration.NODE_RESOURCES_CONFIGURATION_FILE,
        conf);

    for (Map.Entry<String, String> entry : conf) {
      String key = entry.getKey();
      String value = entry.getValue();
      addResourceTypeInformation(key, value, nodeResources);
    }

    return nodeResources;
  }

  private static void addResourceTypeInformation(String prop, String value,
      Map<String, ResourceInformation> nodeResources) {
    if (prop.startsWith(YarnConfiguration.NM_RESOURCES_PREFIX)) {
      LOG.info("Found resource entry " + prop);
      String resourceType = prop.substring(
          YarnConfiguration.NM_RESOURCES_PREFIX.length());
      if (!nodeResources.containsKey(resourceType)) {
        nodeResources
            .put(resourceType, ResourceInformation.newInstance(resourceType));
      }
      String units = getUnits(value);
      Long resourceValue =
          Long.valueOf(value.substring(0, value.length() - units.length()));
      nodeResources.get(resourceType).setValue(resourceValue);
      nodeResources.get(resourceType).setUnits(units);
      LOG.debug("Setting value for resource type " + resourceType + " to "
              + resourceValue + " with units " + units);
    }
  }

  @VisibleForTesting
  synchronized public static void resetNodeResources() {
    initializedNodeResources = false;
  }

  public static Resource getResourceTypesMinimumAllocation() {
    Resource ret = Resource.newInstance(0, 0);
    for (ResourceInformation entry : resourceTypesArray) {
      String name = entry.getName();
      if (name.equals(ResourceInformation.MEMORY_MB.getName())) {
        ret.setMemorySize(entry.getMinimumAllocation());
      } else if (name.equals(ResourceInformation.VCORES.getName())) {
        Long tmp = entry.getMinimumAllocation();
        if (tmp > Integer.MAX_VALUE) {
          tmp = (long) Integer.MAX_VALUE;
        }
        ret.setVirtualCores(tmp.intValue());
      } else {
        ret.setResourceValue(name, entry.getMinimumAllocation());
      }
    }
    return ret;
  }

  /**
   * Get a Resource object with for the maximum allocation possible.
   * @return a Resource object with the maximum allocation for the scheduler
   */
  public static Resource getResourceTypesMaximumAllocation() {
    Resource ret = Resource.newInstance(0, 0);
    for (ResourceInformation entry : resourceTypesArray) {
      ret.setResourceValue(entry.getName(),
          entry.getMaximumAllocation());
    }
    return ret;
  }

  /**
   * Get default unit by given resource type.
   * @param resourceType resourceType
   * @return default unit
   */
  public static String getDefaultUnit(String resourceType) {
    ResourceInformation ri = getResourceTypes().get(resourceType);
    if (ri != null) {
      return ri.getUnits();
    }
    return "";
  }

  /**
   * Get all resource types information from known resource types.
   * @return List of ResourceTypeInfo
   */
  public static List<ResourceTypeInfo> getResourcesTypeInfo() {
    List<ResourceTypeInfo> array = new ArrayList<>();
    // Add all resource types
    Collection<ResourceInformation> resourcesInfo =
        ResourceUtils.getResourceTypes().values();
    for (ResourceInformation resourceInfo : resourcesInfo) {
      array.add(ResourceTypeInfo
          .newInstance(resourceInfo.getName(), resourceInfo.getUnits(),
              resourceInfo.getResourceType()));
    }
    return array;
  }

  /**
   * From a given configuration get all entries representing requested
   * resources: entries that match the {prefix}{resourceName}={value}[{units}]
   * pattern.
   * @param configuration The configuration
   * @param prefix Keys with this prefix are considered from the configuration
   * @return The list of requested resources as described by the configuration
   */
  public static List<ResourceInformation> getRequestedResourcesFromConfig(
      Configuration configuration, String prefix) {
    List<ResourceInformation> result = new ArrayList<>();
    Map<String, String> customResourcesMap = configuration
        .getValByRegex("^" + Pattern.quote(prefix) + "[^.]+$");
    for (Entry<String, String> resource : customResourcesMap.entrySet()) {
      String resourceName = resource.getKey().substring(prefix.length());
      Matcher matcher =
          RESOURCE_REQUEST_VALUE_PATTERN.matcher(resource.getValue());
      if (!matcher.matches()) {
        String errorMsg = "Invalid resource request specified for property "
            + resource.getKey() + ": \"" + resource.getValue()
            + "\", expected format is: value[ ][units]";
        LOG.error(errorMsg);
        throw new IllegalArgumentException(errorMsg);
      }
      long value = Long.parseLong(matcher.group(1));
      String unit = matcher.group(2);
      if (unit.isEmpty()) {
        unit = ResourceUtils.getDefaultUnit(resourceName);
      }
      ResourceInformation resourceInformation = new ResourceInformation();
      resourceInformation.setName(resourceName);
      resourceInformation.setValue(value);
      resourceInformation.setUnits(unit);
      result.add(resourceInformation);
    }
    return result;
  }

}
