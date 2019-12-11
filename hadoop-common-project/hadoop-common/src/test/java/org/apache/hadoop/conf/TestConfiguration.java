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
package org.apache.hadoop.conf;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import static java.util.concurrent.TimeUnit.*;

import junit.framework.TestCase;
import static org.junit.Assert.assertArrayEquals;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration.IntegerRanges;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.apache.hadoop.security.alias.LocalJavaKeyStoreProvider;
import org.apache.hadoop.test.GenericTestUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.mockito.Mockito;

import static org.apache.hadoop.util.PlatformName.IBM_JAVA;
import static org.junit.Assert.fail;

import org.codehaus.jackson.map.ObjectMapper;

public class TestConfiguration extends TestCase {

  private Configuration conf;
  final static String CONFIG = new File("./test-config-TestConfiguration.xml").getAbsolutePath();
  final static String CONFIG2 = new File("./test-config2-TestConfiguration.xml").getAbsolutePath();
  final static String CONFIG_FOR_ENUM = new File("./test-config-enum-TestConfiguration.xml").getAbsolutePath();
  final static String CONFIG_FOR_URI = new File(
      "./test-config-uri-TestConfiguration.xml").toURI().toString();

  private static final String CONFIG_MULTI_BYTE = new File(
    "./test-config-multi-byte-TestConfiguration.xml").getAbsolutePath();
  private static final String CONFIG_MULTI_BYTE_SAVED = new File(
    "./test-config-multi-byte-saved-TestConfiguration.xml").getAbsolutePath();
  final static Random RAN = new Random();
  final static String XMLHEADER = 
            IBM_JAVA?"<?xml version=\"1.0\" encoding=\"UTF-8\"?><configuration>":
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><configuration>";

  /** Four apostrophes. */
  public static final String ESCAPED = "&apos;&#39;&#0039;&#x27;";

  private static final String SENSITIVE_CONFIG_KEYS =
      CommonConfigurationKeysPublic.HADOOP_SECURITY_SENSITIVE_CONFIG_KEYS;

  private BufferedWriter out;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    conf = new Configuration();
  }
  
  @Override
  protected void tearDown() throws Exception {
    if(out != null) {
      out.close();
    }
    super.tearDown();
    new File(CONFIG).delete();
    new File(CONFIG2).delete();
    new File(CONFIG_FOR_ENUM).delete();
    new File(new URI(CONFIG_FOR_URI)).delete();
    new File(CONFIG_MULTI_BYTE).delete();
    new File(CONFIG_MULTI_BYTE_SAVED).delete();
  }
  
  private void startConfig() throws IOException{
    out.write("<?xml version=\"1.0\"?>\n");
    out.write("<configuration>\n");
  }

  private void writeHeader() throws IOException{
    out.write("<?xml version=\"1.0\"?>\n");
  }

  private void writeHeader(String encoding) throws IOException{
    out.write("<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>\n");
  }

  private void writeConfiguration() throws IOException{
    out.write("<configuration>\n");
  }

  private void endConfig() throws IOException{
    out.write("</configuration>\n");
    out.close();
  }

  private void startInclude(String filename) throws IOException {
    out.write("<xi:include href=\"" + filename + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\"  >\n ");
  }

  private void endInclude() throws IOException{
    out.write("</xi:include>\n ");
  }

  private void startFallback() throws IOException {
    out.write("<xi:fallback>\n ");
  }

  private void endFallback() throws IOException {
    out.write("</xi:fallback>\n ");
  }

  private void declareEntity(String root, String entity, String value)
      throws IOException {
    out.write("<!DOCTYPE " + root
        + " [\n<!ENTITY " + entity + " \"" + value + "\">\n]>");
  }

  private void declareSystemEntity(String root, String entity, String value)
      throws IOException {
    out.write("<!DOCTYPE " + root
        + " [\n<!ENTITY " + entity + " SYSTEM \"" + value + "\">\n]>");
  }

  public void testInputStreamResource() throws Exception {
    StringWriter writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "A", "A");
    endConfig();

    InputStream in1 = Mockito.spy(new ByteArrayInputStream(
          writer.toString().getBytes()));
    Configuration conf = new Configuration(false);
    conf.addResource(in1);
    assertEquals("A", conf.get("prop"));
    Mockito.verify(in1, Mockito.times(1)).close();
    InputStream in2 = new ByteArrayInputStream(writer.toString().getBytes());
    conf.addResource(in2);
    assertEquals("A", conf.get("prop"));
  }

  public void testFinalWarnings() throws Exception {
    // Make a configuration file with a final property
    StringWriter writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "A", "A", true);
    endConfig();
    byte[] bytes = writer.toString().getBytes();
    InputStream in1 = new ByteArrayInputStream(bytes);

    // Make a second config file with a final property with a different value
    writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "BB", "BB", true);
    endConfig();
    byte[] bytes2 = writer.toString().getBytes();
    InputStream in2 = new ByteArrayInputStream(bytes2);

    // Attach our own log appender so we can verify output
    TestAppender appender = new TestAppender();
    final Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);

    try {
      // Add the 2 different resources - this should generate a warning
      conf.addResource(in1);
      conf.addResource(in2);
      assertEquals("should see the first value", "A", conf.get("prop"));

      List<LoggingEvent> events = appender.getLog();
      assertEquals("overriding a final parameter should cause logging", 1,
          events.size());
      LoggingEvent loggingEvent = events.get(0);
      String renderedMessage = loggingEvent.getRenderedMessage();
      assertTrue("did not see expected string inside message "+ renderedMessage,
          renderedMessage.contains("an attempt to override final parameter: "
              + "prop;  Ignoring."));
    } finally {
      // Make sure the appender is removed
      logger.removeAppender(appender);
    }
  }

  public void testNoFinalWarnings() throws Exception {
    // Make a configuration file with a final property
    StringWriter writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "A", "A", true);
    endConfig();
    byte[] bytes = writer.toString().getBytes();
    // The 2 input streams both have the same config file
    InputStream in1 = new ByteArrayInputStream(bytes);
    InputStream in2 = new ByteArrayInputStream(bytes);

    // Attach our own log appender so we can verify output
    TestAppender appender = new TestAppender();
    final Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);

    try {
      // Add the resource twice from a stream - should not generate warnings
      conf.addResource(in1);
      conf.addResource(in2);
      assertEquals("A", conf.get("prop"));

      List<LoggingEvent> events = appender.getLog();
      for (LoggingEvent loggingEvent : events) {
        System.out.println("Event = " + loggingEvent.getRenderedMessage());
      }
      assertTrue("adding same resource twice should not cause logging",
          events.isEmpty());
    } finally {
      // Make sure the appender is removed
      logger.removeAppender(appender);
    }
  }



  public void testFinalWarningsMultiple() throws Exception {
    // Make a configuration file with a repeated final property
    StringWriter writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "A", "A", true);
    declareProperty("prop", "A", "A", true);
    endConfig();
    byte[] bytes = writer.toString().getBytes();
    InputStream in1 = new ByteArrayInputStream(bytes);

    // Attach our own log appender so we can verify output
    TestAppender appender = new TestAppender();
    final Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);

    try {
      // Add the resource - this should not produce a warning
      conf.addResource(in1);
      assertEquals("should see the value", "A", conf.get("prop"));

      List<LoggingEvent> events = appender.getLog();
      for (LoggingEvent loggingEvent : events) {
        System.out.println("Event = " + loggingEvent.getRenderedMessage());
      }
      assertTrue("adding same resource twice should not cause logging",
          events.isEmpty());
    } finally {
      // Make sure the appender is removed
      logger.removeAppender(appender);
    }
  }

  public void testFinalWarningsMultipleOverride() throws Exception {
    // Make a configuration file with 2 final properties with different values
    StringWriter writer = new StringWriter();
    out = new BufferedWriter(writer);
    startConfig();
    declareProperty("prop", "A", "A", true);
    declareProperty("prop", "BB", "BB", true);
    endConfig();
    byte[] bytes = writer.toString().getBytes();
    InputStream in1 = new ByteArrayInputStream(bytes);

    // Attach our own log appender so we can verify output
    TestAppender appender = new TestAppender();
    final Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);

    try {
      // Add the resource - this should produce a warning
      conf.addResource(in1);
      assertEquals("should see the value", "A", conf.get("prop"));

      List<LoggingEvent> events = appender.getLog();
      assertEquals("overriding a final parameter should cause logging", 1,
          events.size());
      LoggingEvent loggingEvent = events.get(0);
      String renderedMessage = loggingEvent.getRenderedMessage();
      assertTrue("did not see expected string inside message "+ renderedMessage,
          renderedMessage.contains("an attempt to override final parameter: "
              + "prop;  Ignoring."));
    } finally {
      // Make sure the appender is removed
      logger.removeAppender(appender);
    }
  }

  /**
   * A simple appender for white box testing.
   */
  private static class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> log = new ArrayList<>();

    @Override public boolean requiresLayout() {
      return false;
    }

    @Override protected void append(final LoggingEvent loggingEvent) {
      log.add(loggingEvent);
    }

    @Override public void close() {
    }

    public List<LoggingEvent> getLog() {
      return new ArrayList<>(log);
    }
  }

  /**
   * Tests use of multi-byte characters in property names and values.  This test
   * round-trips multi-byte string literals through saving and loading of config
   * and asserts that the same values were read.
   */
  public void testMultiByteCharacters() throws IOException {
    String priorDefaultEncoding = System.getProperty("file.encoding");
    try {
      System.setProperty("file.encoding", "US-ASCII");
      String name = "multi_byte_\u611b_name";
      String value = "multi_byte_\u0641_value";
      out = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(CONFIG_MULTI_BYTE), "UTF-8"));
      startConfig();
      declareProperty(name, value, value);
      endConfig();

      Configuration conf = new Configuration(false);
      conf.addResource(new Path(CONFIG_MULTI_BYTE));
      assertEquals(value, conf.get(name));
      try (FileOutputStream fos =
               new FileOutputStream(CONFIG_MULTI_BYTE_SAVED)) {
        conf.writeXml(fos);
      }

      conf = new Configuration(false);
      conf.addResource(new Path(CONFIG_MULTI_BYTE_SAVED));
      assertEquals(value, conf.get(name));
    } finally {
      System.setProperty("file.encoding", priorDefaultEncoding);
    }
  }

  public void testVariableSubstitution() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    declareProperty("my.int", "${intvar}", "42");
    declareProperty("intvar", "42", "42");
    declareProperty("my.base", "/tmp/${user.name}", UNSPEC);
    declareProperty("my.file", "hello", "hello");
    declareProperty("my.suffix", ".txt", ".txt");
    declareProperty("my.relfile", "${my.file}${my.suffix}", "hello.txt");
    declareProperty("my.fullfile", "${my.base}/${my.file}${my.suffix}", UNSPEC);
    // check that undefined variables are returned as-is
    declareProperty("my.failsexpand", "a${my.undefvar}b", "a${my.undefvar}b");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);

    for (Prop p : props) {
      System.out.println("p=" + p.name);
      String gotVal = conf.get(p.name);
      String gotRawVal = conf.getRaw(p.name);
      assertEq(p.val, gotRawVal);
      if (p.expectEval == UNSPEC) {
        // expansion is system-dependent (uses System properties)
        // can't do exact match so just check that all variables got expanded
        assertTrue(gotVal != null && -1 == gotVal.indexOf("${"));
      } else {
        assertEq(p.expectEval, gotVal);
      }
    }
      
    // check that expansion also occurs for getInt()
    assertTrue(conf.getInt("intvar", -1) == 42);
    assertTrue(conf.getInt("my.int", -1) == 42);

    Map<String, String> results = conf.getValByRegex("^my.*file$");
    assertTrue(results.keySet().contains("my.relfile"));
    assertTrue(results.keySet().contains("my.fullfile"));
    assertTrue(results.keySet().contains("my.file"));
    assertEquals(-1, results.get("my.relfile").indexOf("${"));
    assertEquals(-1, results.get("my.fullfile").indexOf("${"));
    assertEquals(-1, results.get("my.file").indexOf("${"));
  }

  public void testFinalParam() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    declareProperty("my.var", "", "", true);
    endConfig();
    Path fileResource = new Path(CONFIG);
    Configuration conf1 = new Configuration();
    conf1.addResource(fileResource);
    assertNull("my var is not null", conf1.get("my.var"));
	
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    declareProperty("my.var", "myval", "myval", false);
    endConfig();
    fileResource = new Path(CONFIG2);

    Configuration conf2 = new Configuration(conf1);
    conf2.addResource(fileResource);
    assertNull("my var is not final", conf2.get("my.var"));
  }

  public static void assertEq(Object a, Object b) {
    System.out.println("assertEq: " + a + ", " + b);
    assertEquals(a, b);
  }

  static class Prop {
    String name;
    String val;
    String expectEval;
  }

  final String UNSPEC = null;
  ArrayList<Prop> props = new ArrayList<Prop>();

  void declareProperty(String name, String val, String expectEval)
    throws IOException {
    declareProperty(name, val, expectEval, false);
  }

  void declareProperty(String name, String val, String expectEval,
                       boolean isFinal)
    throws IOException {
    appendProperty(name, val, isFinal);
    Prop p = new Prop();
    p.name = name;
    p.val = val;
    p.expectEval = expectEval;
    props.add(p);
  }

  void appendProperty(String name, String val) throws IOException {
    appendProperty(name, val, false);
  }
 
  void appendProperty(String name, String val, boolean isFinal, 
      String ... sources)
    throws IOException {
    out.write("<property>");
    out.write("<name>");
    out.write(name);
    out.write("</name>");
    out.write("<value>");
    out.write(val);
    out.write("</value>");
    if (isFinal) {
      out.write("<final>true</final>");
    }
    for(String s : sources) {
      out.write("<source>");
      out.write(s);
      out.write("</source>");
    }
    out.write("</property>\n");
  }
  
  public void testOverlay() throws IOException{
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("a","b");
    appendProperty("b","c");
    appendProperty("d","e");
    appendProperty("e","f", true);
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("a","b");
    appendProperty("b","d");
    appendProperty("e","e");
    endConfig();
    
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    
    //set dynamically something
    conf.set("c","d");
    conf.set("a","d");
    
    Configuration clone=new Configuration(conf);
    clone.addResource(new Path(CONFIG2));
    
    assertEquals(clone.get("a"), "d"); 
    assertEquals(clone.get("b"), "d"); 
    assertEquals(clone.get("c"), "d"); 
    assertEquals(clone.get("d"), "e"); 
    assertEquals(clone.get("e"), "f"); 
    
  }
  
  public void testCommentsInValue() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("my.comment", "this <!--comment here--> contains a comment");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    //two spaces one after "this", one before "contains"
    assertEquals("this  contains a comment", conf.get("my.comment"));
  }

  public void testEscapedCharactersInValue() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("my.comment", ESCAPED);
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    //two spaces one after "this", one before "contains"
    assertEquals("''''", conf.get("my.comment"));
  }

  public void testTrim() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    String[] whitespaces = {"", " ", "\n", "\t"};
    String[] name = new String[100];
    for(int i = 0; i < name.length; i++) {
      name[i] = "foo" + i;
      StringBuilder prefix = new StringBuilder(); 
      StringBuilder postfix = new StringBuilder(); 
      for(int j = 0; j < 3; j++) {
        prefix.append(whitespaces[RAN.nextInt(whitespaces.length)]);
        postfix.append(whitespaces[RAN.nextInt(whitespaces.length)]);
      }
      
      appendProperty(prefix + name[i] + postfix, name[i] + ".value");
    }
    endConfig();

    conf.addResource(new Path(CONFIG));
    for(String n : name) {
      assertEquals(n + ".value", conf.get(n));
    }
  }

  public void testGetLocalPath() throws IOException {
    Configuration conf = new Configuration();
    String[] dirs = new String[]{"a", "b", "c"};
    for (int i = 0; i < dirs.length; i++) {
      dirs[i] = new Path(GenericTestUtils.getTempPath(dirs[i])).toString();
    }
    conf.set("dirs", StringUtils.join(dirs, ","));
    for (int i = 0; i < 1000; i++) {
      String localPath = conf.getLocalPath("dirs", "dir" + i).toString();
      assertTrue("Path doesn't end in specified dir: " + localPath,
        localPath.endsWith("dir" + i));
      assertFalse("Path has internal whitespace: " + localPath,
        localPath.contains(" "));
    }
  }
  
  public void testGetFile() throws IOException {
    Configuration conf = new Configuration();
    String[] dirs = new String[]{"a", "b", "c"};
    for (int i = 0; i < dirs.length; i++) {
      dirs[i] = new Path(GenericTestUtils.getTempPath(dirs[i])).toString();
    }
    conf.set("dirs", StringUtils.join(dirs, ","));
    for (int i = 0; i < 1000; i++) {
      String localPath = conf.getFile("dirs", "dir" + i).toString();
      assertTrue("Path doesn't end in specified dir: " + localPath,
        localPath.endsWith("dir" + i));
      assertFalse("Path has internal whitespace: " + localPath,
        localPath.contains(" "));
    }
  }

  public void testToString() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    
    String expectedOutput = 
      "Configuration: core-default.xml, core-site.xml, " + 
      fileResource.toString();
    assertEquals(expectedOutput, conf.toString());
  }
  
  public void testWriteXml() throws IOException {
    Configuration conf = new Configuration();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
    conf.writeXml(baos);
    String result = baos.toString();
    assertTrue("Result has proper header", result.startsWith(XMLHEADER));
	  
    assertTrue("Result has proper footer", result.endsWith("</configuration>"));
  }
  
  public void testIncludes() throws Exception {
    tearDown();
    System.out.println("XXX testIncludes");
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("a","b");
    appendProperty("c","d");
    endConfig();
    File fileUri = new File(new URI(CONFIG_FOR_URI));
    out=new BufferedWriter(new FileWriter(fileUri));
    startConfig();
    appendProperty("e", "f");
    appendProperty("g", "h");
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    startInclude(CONFIG2);
    endInclude();
    startInclude(CONFIG_FOR_URI);
    endInclude();
    appendProperty("i", "j");
    appendProperty("k", "l");
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(conf.get("a"), "b"); 
    assertEquals(conf.get("c"), "d"); 
    assertEquals(conf.get("e"), "f");
    assertEquals(conf.get("g"), "h");
    assertEquals(conf.get("i"), "j");
    assertEquals(conf.get("k"), "l");
    tearDown();
  }

  public void testCharsetInDocumentEncoding() throws Exception {
    tearDown();
    out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CONFIG),
        StandardCharsets.ISO_8859_1));
    writeHeader(StandardCharsets.ISO_8859_1.displayName());
    writeConfiguration();
    appendProperty("a", "b");
    appendProperty("c", "Müller");
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(conf.get("a"), "b");
    assertEquals(conf.get("c"), "Müller");
    tearDown();
  }

  public void testEntityReference() throws Exception {
    tearDown();
    out=new BufferedWriter(new FileWriter(CONFIG));
    writeHeader();
    declareEntity("configuration", "d", "d");
    writeConfiguration();
    appendProperty("a", "b");
    appendProperty("c", "&d;");
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(conf.get("a"), "b");
    assertEquals(conf.get("c"), "d");
    tearDown();
  }

  public void testSystemEntityReference() throws Exception {
    tearDown();
    out=new BufferedWriter(new FileWriter(CONFIG2));
    out.write("d");
    out.close();
    out=new BufferedWriter(new FileWriter(CONFIG));
    writeHeader();
    declareSystemEntity("configuration", "d",
        new Path(CONFIG2).toUri().toString());
    writeConfiguration();
    appendProperty("a", "b");
    appendProperty("c", "&d;");
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(conf.get("a"), "b");
    assertEquals(conf.get("c"), "d");
    tearDown();
  }

  public void testIncludesWithFallback() throws Exception {
    tearDown();
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("a","b");
    appendProperty("c","d");
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    startInclude(CONFIG2);
    startFallback();
    appendProperty("a", "b.fallback");
    appendProperty("c", "d.fallback", true);
    endFallback();
    endInclude();
    appendProperty("e","f");
    appendProperty("g","h");
    startInclude("MissingConfig.xml");
    startFallback();
    appendProperty("i", "j.fallback");
    appendProperty("k", "l.fallback", true);
    endFallback();
    endInclude();
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals("b", conf.get("a"));
    assertEquals("d", conf.get("c"));
    assertEquals("f", conf.get("e"));
    assertEquals("h", conf.get("g"));
    assertEquals("j.fallback", conf.get("i"));
    assertEquals("l.fallback", conf.get("k"));
    tearDown();
  }

  // When a resource is parsed as an input stream the first time, included
  // properties are saved within the config. However, the included properties
  // are not cached in the resource object. So, if an additional resource is
  // added after the config is parsed the first time, the config loses the
  // prperties that were included from the first resource.
  public void testIncludesFromInputStreamWhenResourceAdded() throws Exception {
    tearDown();

    // CONFIG includes CONFIG2. CONFIG2 includes CONFIG_FOR_ENUM
    out=new BufferedWriter(new FileWriter(CONFIG_FOR_ENUM));
    startConfig();
    appendProperty("e", "SecondLevelInclude");
    appendProperty("f", "SecondLevelInclude");
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    startInclude(CONFIG_FOR_ENUM);
    endInclude();
    appendProperty("c","FirstLevelInclude");
    appendProperty("d","FirstLevelInclude");
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    startInclude(CONFIG2);
    endInclude();
    appendProperty("a", "1");
    appendProperty("b", "2");
    endConfig();

    // Add CONFIG as an InputStream resource.
    File file = new File(CONFIG);
    BufferedInputStream bis =
        new BufferedInputStream(new FileInputStream(file));
    conf.addResource(bis);

    // The first time the conf is parsed, verify that all properties were read
    // from all levels of includes.
    assertEquals("1", conf.get("a"));
    assertEquals("2", conf.get("b"));
    assertEquals("FirstLevelInclude", conf.get("c"));
    assertEquals("FirstLevelInclude", conf.get("d"));
    assertEquals("SecondLevelInclude", conf.get("e"));
    assertEquals("SecondLevelInclude", conf.get("f"));

    // Add another resource to the conf.
    out=new BufferedWriter(new FileWriter(CONFIG_MULTI_BYTE));
    startConfig();
    appendProperty("g", "3");
    appendProperty("h", "4");
    endConfig();

    Path fileResource = new Path(CONFIG_MULTI_BYTE);
    conf.addResource(fileResource);

    // Verify that all properties were read from all levels of includes the
    // second time the conf is parsed.
    assertEquals("1", conf.get("a"));
    assertEquals("2", conf.get("b"));
    assertEquals("FirstLevelInclude", conf.get("c"));
    assertEquals("FirstLevelInclude", conf.get("d"));
    assertEquals("SecondLevelInclude", conf.get("e"));
    assertEquals("SecondLevelInclude", conf.get("f"));
    assertEquals("3", conf.get("g"));
    assertEquals("4", conf.get("h"));

    tearDown();
  }

  public void testOrderOfDuplicatePropertiesWithInclude() throws Exception {
    tearDown();

    // Property "a" is set to different values inside and outside of includes.
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("a", "a-InsideInclude");
    appendProperty("b", "b-InsideInclude");
    endConfig();

    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("a","a-OutsideInclude");
    startInclude(CONFIG2);
    endInclude();
    appendProperty("b","b-OutsideInclude");
    endConfig();

    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);

    assertEquals("a-InsideInclude", conf.get("a"));
    assertEquals("b-OutsideInclude", conf.get("b"));

    tearDown();
  }

  public void testRelativeIncludes() throws Exception {
    tearDown();
    String relConfig = new File("./tmp/test-config.xml").getAbsolutePath();
    String relConfig2 = new File("./tmp/test-config2.xml").getAbsolutePath();

    new File(new File(relConfig).getParent()).mkdirs();
    out = new BufferedWriter(new FileWriter(relConfig2));
    startConfig();
    appendProperty("a", "b");
    endConfig();

    out = new BufferedWriter(new FileWriter(relConfig));
    startConfig();
    // Add the relative path instead of the absolute one.
    startInclude(new File(relConfig2).getName());
    endInclude();
    appendProperty("c", "d");
    endConfig();

    // verify that the includes file contains all properties
    Path fileResource = new Path(relConfig);
    conf.addResource(fileResource);
    assertEquals(conf.get("a"), "b");
    assertEquals(conf.get("c"), "d");

    // Cleanup
    new File(relConfig).delete();
    new File(relConfig2).delete();
    new File(new File(relConfig).getParent()).delete();
  }

  public void testIntegerRanges() {
    Configuration conf = new Configuration();
    conf.set("first", "-100");
    conf.set("second", "4-6,9-10,27");
    conf.set("third", "34-");
    Configuration.IntegerRanges range = conf.getRange("first", null);
    System.out.println("first = " + range);
    assertEquals(true, range.isIncluded(0));
    assertEquals(true, range.isIncluded(1));
    assertEquals(true, range.isIncluded(100));
    assertEquals(false, range.isIncluded(101));
    range = conf.getRange("second", null);
    System.out.println("second = " + range);
    assertEquals(false, range.isIncluded(3));
    assertEquals(true, range.isIncluded(4));
    assertEquals(true, range.isIncluded(6));
    assertEquals(false, range.isIncluded(7));
    assertEquals(false, range.isIncluded(8));
    assertEquals(true, range.isIncluded(9));
    assertEquals(true, range.isIncluded(10));
    assertEquals(false, range.isIncluded(11));
    assertEquals(false, range.isIncluded(26));
    assertEquals(true, range.isIncluded(27));
    assertEquals(false, range.isIncluded(28));
    range = conf.getRange("third", null);
    System.out.println("third = " + range);
    assertEquals(false, range.isIncluded(33));
    assertEquals(true, range.isIncluded(34));
    assertEquals(true, range.isIncluded(100000000));
  }
  
  public void testGetRangeIterator() throws Exception {
    Configuration config = new Configuration(false);
    IntegerRanges ranges = config.getRange("Test", "");
    assertFalse("Empty range has values", ranges.iterator().hasNext());
    ranges = config.getRange("Test", "5");
    Set<Integer> expected = new HashSet<Integer>(Arrays.asList(5));
    Set<Integer> found = new HashSet<Integer>();
    for(Integer i: ranges) {
      found.add(i);
    }
    assertEquals(expected, found);

    ranges = config.getRange("Test", "5-10,13-14");
    expected = new HashSet<Integer>(Arrays.asList(5,6,7,8,9,10,13,14));
    found = new HashSet<Integer>();
    for(Integer i: ranges) {
      found.add(i);
    }
    assertEquals(expected, found);
    
    ranges = config.getRange("Test", "8-12, 5- 7");
    expected = new HashSet<Integer>(Arrays.asList(5,6,7,8,9,10,11,12));
    found = new HashSet<Integer>();
    for(Integer i: ranges) {
      found.add(i);
    }
    assertEquals(expected, found);
  }

  public void testHexValues() throws IOException{
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.hex1", "0x10");
    appendProperty("test.hex2", "0xF");
    appendProperty("test.hex3", "-0x10");
    // Invalid?
    appendProperty("test.hex4", "-0x10xyz");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(16, conf.getInt("test.hex1", 0));
    assertEquals(16, conf.getLong("test.hex1", 0));
    assertEquals(15, conf.getInt("test.hex2", 0));
    assertEquals(15, conf.getLong("test.hex2", 0));
    assertEquals(-16, conf.getInt("test.hex3", 0));
    assertEquals(-16, conf.getLong("test.hex3", 0));
    try {
      conf.getLong("test.hex4", 0);
      fail("Property had invalid long value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
    try {
      conf.getInt("test.hex4", 0);
      fail("Property had invalid int value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
  }

  public void testIntegerValues() throws IOException{
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.int1", "20");
    appendProperty("test.int2", "020");
    appendProperty("test.int3", "-20");
    appendProperty("test.int4", " -20 ");
    appendProperty("test.int5", " -20xyz ");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(20, conf.getInt("test.int1", 0));
    assertEquals(20, conf.getLong("test.int1", 0));
    assertEquals(20, conf.getLongBytes("test.int1", 0));
    assertEquals(20, conf.getInt("test.int2", 0));
    assertEquals(20, conf.getLong("test.int2", 0));
    assertEquals(20, conf.getLongBytes("test.int2", 0));
    assertEquals(-20, conf.getInt("test.int3", 0));
    assertEquals(-20, conf.getLong("test.int3", 0));
    assertEquals(-20, conf.getLongBytes("test.int3", 0));
    assertEquals(-20, conf.getInt("test.int4", 0));
    assertEquals(-20, conf.getLong("test.int4", 0));
    assertEquals(-20, conf.getLongBytes("test.int4", 0));
    try {
      conf.getInt("test.int5", 0);
      fail("Property had invalid int value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
  }
  
  public void testHumanReadableValues() throws IOException {
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.humanReadableValue1", "1m");
    appendProperty("test.humanReadableValue2", "1M");
    appendProperty("test.humanReadableValue5", "1MBCDE");

    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(1048576, conf.getLongBytes("test.humanReadableValue1", 0));
    assertEquals(1048576, conf.getLongBytes("test.humanReadableValue2", 0));
    try {
      conf.getLongBytes("test.humanReadableValue5", 0);
      fail("Property had invalid human readable value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
  }

  public void testBooleanValues() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.bool1", "true");
    appendProperty("test.bool2", "false");
    appendProperty("test.bool3", "  true ");
    appendProperty("test.bool4", " false ");
    appendProperty("test.bool5", "foo");
    appendProperty("test.bool6", "TRUE");
    appendProperty("test.bool7", "FALSE");
    appendProperty("test.bool8", "");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(true, conf.getBoolean("test.bool1", false));
    assertEquals(false, conf.getBoolean("test.bool2", true));
    assertEquals(true, conf.getBoolean("test.bool3", false));
    assertEquals(false, conf.getBoolean("test.bool4", true));
    assertEquals(true, conf.getBoolean("test.bool5", true));
    assertEquals(true, conf.getBoolean("test.bool6", false));
    assertEquals(false, conf.getBoolean("test.bool7", true));
    assertEquals(false, conf.getBoolean("test.bool8", false));
  }
  
  public void testFloatValues() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.float1", "3.1415");
    appendProperty("test.float2", "003.1415");
    appendProperty("test.float3", "-3.1415");
    appendProperty("test.float4", " -3.1415 ");
    appendProperty("test.float5", "xyz-3.1415xyz");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(3.1415f, conf.getFloat("test.float1", 0.0f));
    assertEquals(3.1415f, conf.getFloat("test.float2", 0.0f));
    assertEquals(-3.1415f, conf.getFloat("test.float3", 0.0f));
    assertEquals(-3.1415f, conf.getFloat("test.float4", 0.0f));
    try {
      conf.getFloat("test.float5", 0.0f);
      fail("Property had invalid float value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
  }
  
  public void testDoubleValues() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.double1", "3.1415");
    appendProperty("test.double2", "003.1415");
    appendProperty("test.double3", "-3.1415");
    appendProperty("test.double4", " -3.1415 ");
    appendProperty("test.double5", "xyz-3.1415xyz");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals(3.1415, conf.getDouble("test.double1", 0.0));
    assertEquals(3.1415, conf.getDouble("test.double2", 0.0));
    assertEquals(-3.1415, conf.getDouble("test.double3", 0.0));
    assertEquals(-3.1415, conf.getDouble("test.double4", 0.0));
    try {
      conf.getDouble("test.double5", 0.0);
      fail("Property had invalid double value, but was read successfully.");
    } catch (NumberFormatException e) {
      // pass
    }
  }
  
  public void testGetClass() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.class1", "java.lang.Integer");
    appendProperty("test.class2", " java.lang.Integer ");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals("java.lang.Integer", conf.getClass("test.class1", null).getCanonicalName());
    assertEquals("java.lang.Integer", conf.getClass("test.class2", null).getCanonicalName());
  }
  
  public void testGetClasses() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.classes1", "java.lang.Integer,java.lang.String");
    appendProperty("test.classes2", " java.lang.Integer , java.lang.String ");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    String[] expectedNames = {"java.lang.Integer", "java.lang.String"};
    Class<?>[] defaultClasses = {};
    Class<?>[] classes1 = conf.getClasses("test.classes1", defaultClasses);
    Class<?>[] classes2 = conf.getClasses("test.classes2", defaultClasses);
    assertArrayEquals(expectedNames, extractClassNames(classes1));
    assertArrayEquals(expectedNames, extractClassNames(classes2));
  }
  
  public void testGetStringCollection() throws IOException {
    Configuration c = new Configuration();
    c.set("x", " a, b\n,\nc ");
    Collection<String> strs = c.getTrimmedStringCollection("x");
    assertEquals(3, strs.size());
    assertArrayEquals(new String[]{ "a", "b", "c" },
                      strs.toArray(new String[0]));

    // Check that the result is mutable
    strs.add("z");

    // Make sure same is true for missing config
    strs = c.getStringCollection("does-not-exist");
    assertEquals(0, strs.size());
    strs.add("z");
  }

  public void testGetTrimmedStringCollection() throws IOException {
    Configuration c = new Configuration();
    c.set("x", "a, b, c");
    Collection<String> strs = c.getStringCollection("x");
    assertEquals(3, strs.size());
    assertArrayEquals(new String[]{ "a", " b", " c" },
                      strs.toArray(new String[0]));

    // Check that the result is mutable
    strs.add("z");

    // Make sure same is true for missing config
    strs = c.getStringCollection("does-not-exist");
    assertEquals(0, strs.size());
    strs.add("z");
  }

  private static String[] extractClassNames(Class<?>[] classes) {
    String[] classNames = new String[classes.length];
    for (int i = 0; i < classNames.length; i++) {
      classNames[i] = classes[i].getCanonicalName();
    }
    return classNames;
  }
  
  enum Dingo { FOO, BAR };
  enum Yak { RAB, FOO };
  public void testEnum() throws IOException {
    Configuration conf = new Configuration();
    conf.setEnum("test.enum", Dingo.FOO);
    assertSame(Dingo.FOO, conf.getEnum("test.enum", Dingo.BAR));
    assertSame(Yak.FOO, conf.getEnum("test.enum", Yak.RAB));
    conf.setEnum("test.enum", Dingo.FOO);
    boolean fail = false;
    try {
      conf.setEnum("test.enum", Dingo.BAR);
      Yak y = conf.getEnum("test.enum", Yak.FOO);
    } catch (IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  public void testEnumFromXml() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG_FOR_ENUM));
    startConfig();
    appendProperty("test.enum"," \t \n   FOO \t \n");
    appendProperty("test.enum2"," \t \n   Yak.FOO \t \n");
    endConfig();

    Configuration conf = new Configuration();
    Path fileResource = new Path(CONFIG_FOR_ENUM);
    conf.addResource(fileResource);
    assertSame(Yak.FOO, conf.getEnum("test.enum", Yak.FOO));
    boolean fail = false;
    try {
      conf.getEnum("test.enum2", Yak.FOO);
    } catch (IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  public void testTimeDuration() {
    Configuration conf = new Configuration(false);
    conf.setTimeDuration("test.time.a", 7L, SECONDS);
    assertEquals("7s", conf.get("test.time.a"));
    assertEquals(0L, conf.getTimeDuration("test.time.a", 30, MINUTES));
    assertEquals(7L, conf.getTimeDuration("test.time.a", 30, SECONDS));
    assertEquals(7000L, conf.getTimeDuration("test.time.a", 30, MILLISECONDS));
    assertEquals(7000000L,
        conf.getTimeDuration("test.time.a", 30, MICROSECONDS));
    assertEquals(7000000000L,
        conf.getTimeDuration("test.time.a", 30, NANOSECONDS));
    conf.setTimeDuration("test.time.b", 1, DAYS);
    assertEquals("1d", conf.get("test.time.b"));
    assertEquals(1, conf.getTimeDuration("test.time.b", 1, DAYS));
    assertEquals(24, conf.getTimeDuration("test.time.b", 1, HOURS));
    assertEquals(MINUTES.convert(1, DAYS),
        conf.getTimeDuration("test.time.b", 1, MINUTES));

    // check default
    assertEquals(30L, conf.getTimeDuration("test.time.X", 30, SECONDS));
    conf.set("test.time.X", "30");
    assertEquals(30L, conf.getTimeDuration("test.time.X", 40, SECONDS));

    for (Configuration.ParsedTimeDuration ptd :
         Configuration.ParsedTimeDuration.values()) {
      conf.setTimeDuration("test.time.unit", 1, ptd.unit());
      assertEquals(1 + ptd.suffix(), conf.get("test.time.unit"));
      assertEquals(1, conf.getTimeDuration("test.time.unit", 2, ptd.unit()));
    }
  }

  public void testPattern() throws IOException {
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.pattern1", "");
    appendProperty("test.pattern2", "(");
    appendProperty("test.pattern3", "a+b");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);

    Pattern defaultPattern = Pattern.compile("x+");
    // Return default if missing
    assertEquals(defaultPattern.pattern(),
                 conf.getPattern("xxxxx", defaultPattern).pattern());
    // Return null if empty and default is null
    assertNull(conf.getPattern("test.pattern1", null));
    // Return default for empty
    assertEquals(defaultPattern.pattern(),
                 conf.getPattern("test.pattern1", defaultPattern).pattern());
    // Return default for malformed
    assertEquals(defaultPattern.pattern(),
                 conf.getPattern("test.pattern2", defaultPattern).pattern());
    // Works for correct patterns
    assertEquals("a+b",
                 conf.getPattern("test.pattern3", defaultPattern).pattern());
  }

  public void testPropertySource() throws IOException {
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.foo", "bar");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    conf.set("fs.defaultFS", "value");
    String [] sources = conf.getPropertySources("test.foo");
    assertEquals(1, sources.length);
    assertEquals(
        "Resource string returned for a file-loaded property" +
        " must be a proper absolute path",
        fileResource,
        new Path(sources[0]));
    assertArrayEquals("Resource string returned for a set() property must be " +
    		"\"programatically\"",
        new String[]{"programatically"},
        conf.getPropertySources("fs.defaultFS"));
    assertEquals("Resource string returned for an unset property must be null",
        null, conf.getPropertySources("fs.defaultFoo"));
  }
  
  public void testMultiplePropertySource() throws IOException {
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.foo", "bar", false, "a", "b", "c");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    String [] sources = conf.getPropertySources("test.foo");
    assertEquals(4, sources.length);
    assertEquals("a", sources[0]);
    assertEquals("b", sources[1]);
    assertEquals("c", sources[2]);
    assertEquals(
        "Resource string returned for a file-loaded property" +
        " must be a proper absolute path",
        fileResource,
        new Path(sources[3]));
  }

  public void testSocketAddress() throws IOException {
    Configuration conf = new Configuration();
    final String defaultAddr = "host:1";
    final int defaultPort = 2;
    InetSocketAddress addr = null;
    
    addr = conf.getSocketAddr("myAddress", defaultAddr, defaultPort);
    assertEquals(defaultAddr, NetUtils.getHostPortString(addr));
    
    conf.set("myAddress", "host2");
    addr = conf.getSocketAddr("myAddress", defaultAddr, defaultPort);
    assertEquals("host2:"+defaultPort, NetUtils.getHostPortString(addr));
    
    conf.set("myAddress", "host2:3");
    addr = conf.getSocketAddr("myAddress", defaultAddr, defaultPort);
    assertEquals("host2:3", NetUtils.getHostPortString(addr));

    conf.set("myAddress", " \n \t    host4:5     \t \n   ");
    addr = conf.getSocketAddr("myAddress", defaultAddr, defaultPort);
    assertEquals("host4:5", NetUtils.getHostPortString(addr));

    boolean threwException = false;
    conf.set("myAddress", "bad:-port");
    try {
      addr = conf.getSocketAddr("myAddress", defaultAddr, defaultPort);
    } catch (IllegalArgumentException iae) {
      threwException = true;
      assertEquals("Does not contain a valid host:port authority: " +
                   "bad:-port (configuration property 'myAddress')",
                   iae.getMessage());
      
    } finally {
      assertTrue(threwException);
    }
  }

  public void testSetSocketAddress() throws IOException {
    Configuration conf = new Configuration();
    NetUtils.addStaticResolution("host", "127.0.0.1");
    final String defaultAddr = "host:1";
    
    InetSocketAddress addr = NetUtils.createSocketAddr(defaultAddr);    
    conf.setSocketAddr("myAddress", addr);
    assertEquals(defaultAddr, NetUtils.getHostPortString(addr));
  }
  
  public void testUpdateSocketAddress() throws IOException {
    InetSocketAddress addr = NetUtils.createSocketAddrForHost("host", 1);
    InetSocketAddress connectAddr = conf.updateConnectAddr("myAddress", addr);
    assertEquals(connectAddr.getHostName(), addr.getHostName());
    
    addr = new InetSocketAddress(1);
    connectAddr = conf.updateConnectAddr("myAddress", addr);
    assertEquals(connectAddr.getHostName(),
                 InetAddress.getLocalHost().getHostName());
  }

  public void testReload() throws IOException {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.key1", "final-value1", true);
    appendProperty("test.key2", "value2");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("test.key1", "value1");
    appendProperty("test.key3", "value3");
    endConfig();
    Path fileResource1 = new Path(CONFIG2);
    conf.addResource(fileResource1);
    
    // add a few values via set.
    conf.set("test.key3", "value4");
    conf.set("test.key4", "value5");
    
    assertEquals("final-value1", conf.get("test.key1"));
    assertEquals("value2", conf.get("test.key2"));
    assertEquals("value4", conf.get("test.key3"));
    assertEquals("value5", conf.get("test.key4"));
    
    // change values in the test file...
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.key1", "final-value1");
    appendProperty("test.key3", "final-value3", true);
    endConfig();
    
    conf.reloadConfiguration();
    assertEquals("value1", conf.get("test.key1"));
    // overlayed property overrides.
    assertEquals("value4", conf.get("test.key3"));
    assertEquals(null, conf.get("test.key2"));
    assertEquals("value5", conf.get("test.key4"));
  }

  public void testSize() throws IOException {
    Configuration conf = new Configuration(false);
    conf.set("a", "A");
    conf.set("b", "B");
    assertEquals(2, conf.size());
  }

  public void testClear() throws IOException {
    Configuration conf = new Configuration(false);
    conf.set("a", "A");
    conf.set("b", "B");
    conf.clear();
    assertEquals(0, conf.size());
    assertFalse(conf.iterator().hasNext());
  }

  public static class Fake_ClassLoader extends ClassLoader {
  }

  public void testClassLoader() {
    Configuration conf = new Configuration(false);
    conf.setQuietMode(false);
    conf.setClassLoader(new Fake_ClassLoader());
    Configuration other = new Configuration(conf);
    assertTrue(other.getClassLoader() instanceof Fake_ClassLoader);
  }
  
  static class JsonConfiguration {
    JsonProperty[] properties;

    public JsonProperty[] getProperties() {
      return properties;
    }

    public void setProperties(JsonProperty[] properties) {
      this.properties = properties;
    }
  }

  static class SingleJsonConfiguration {
    private JsonProperty property;

    public JsonProperty getProperty() {
      return property;
    }

    public void setProperty(JsonProperty property) {
      this.property = property;
    }
  }

  static class JsonProperty {
    String key;
    public String getKey() {
      return key;
    }
    public void setKey(String key) {
      this.key = key;
    }
    public String getValue() {
      return value;
    }
    public void setValue(String value) {
      this.value = value;
    }
    public boolean getIsFinal() {
      return isFinal;
    }
    public void setIsFinal(boolean isFinal) {
      this.isFinal = isFinal;
    }
    public String getResource() {
      return resource;
    }
    public void setResource(String resource) {
      this.resource = resource;
    }
    String value;
    boolean isFinal;
    String resource;
  }

  private Configuration getActualConf(String xmlStr) {
    Configuration ac = new Configuration(false);
    InputStream in = new ByteArrayInputStream(xmlStr.getBytes());
    ac.addResource(in);
    return ac;
  }

  public void testGetSetTrimmedNames() throws IOException {
    Configuration conf = new Configuration(false);
    conf.set(" name", "value");
    assertEquals("value", conf.get("name"));
    assertEquals("value", conf.get(" name"));
    assertEquals("value", conf.getRaw("  name  "));
  }

  public void testDumpProperty() throws IOException {
    StringWriter outWriter = new StringWriter();
    ObjectMapper mapper = new ObjectMapper();
    String jsonStr = null;
    String xmlStr = null;
    try {
      Configuration testConf = new Configuration(false);
      out = new BufferedWriter(new FileWriter(CONFIG));
      startConfig();
      appendProperty("test.key1", "value1");
      appendProperty("test.key2", "value2", true);
      appendProperty("test.key3", "value3");
      endConfig();
      Path fileResource = new Path(CONFIG);
      testConf.addResource(fileResource);
      out.close();

      // case 1: dump an existing property
      // test json format
      outWriter = new StringWriter();
      Configuration.dumpConfiguration(testConf, "test.key2", outWriter);
      jsonStr = outWriter.toString();
      outWriter.close();
      mapper = new ObjectMapper();
      SingleJsonConfiguration jconf1 =
          mapper.readValue(jsonStr, SingleJsonConfiguration.class);
      JsonProperty jp1 = jconf1.getProperty();
      assertEquals("test.key2", jp1.getKey());
      assertEquals("value2", jp1.getValue());
      assertEquals(true, jp1.isFinal);
      assertEquals(fileResource.toString(), jp1.getResource());

      // test xml format
      outWriter = new StringWriter();
      testConf.writeXml("test.key2", outWriter);
      xmlStr = outWriter.toString();
      outWriter.close();
      Configuration actualConf1 = getActualConf(xmlStr);
      assertEquals(1, actualConf1.size());
      assertEquals("value2", actualConf1.get("test.key2"));
      assertTrue(actualConf1.getFinalParameters().contains("test.key2"));
      assertEquals(fileResource.toString(),
          actualConf1.getPropertySources("test.key2")[0]);

      // case 2: dump an non existing property
      // test json format
      try {
        outWriter = new StringWriter();
        Configuration.dumpConfiguration(testConf,
            "test.unknown.key", outWriter);
        outWriter.close();
      } catch (Exception e) {
        assertTrue(e instanceof IllegalArgumentException);
        assertTrue(e.getMessage().contains("test.unknown.key") &&
            e.getMessage().contains("not found"));
      }
      // test xml format
      try {
        outWriter = new StringWriter();
        testConf.writeXml("test.unknown.key", outWriter);
        outWriter.close();
      } catch (Exception e) {
        assertTrue(e instanceof IllegalArgumentException);
        assertTrue(e.getMessage().contains("test.unknown.key") &&
            e.getMessage().contains("not found"));
      }

      // case 3: specify a null property, ensure all configurations are dumped
      outWriter = new StringWriter();
      Configuration.dumpConfiguration(testConf, null, outWriter);
      jsonStr = outWriter.toString();
      mapper = new ObjectMapper();
      JsonConfiguration jconf3 =
          mapper.readValue(jsonStr, JsonConfiguration.class);
      assertEquals(3, jconf3.getProperties().length);

      outWriter = new StringWriter();
      testConf.writeXml(null, outWriter);
      xmlStr = outWriter.toString();
      outWriter.close();
      Configuration actualConf3 = getActualConf(xmlStr);
      assertEquals(3, actualConf3.size());
      assertTrue(actualConf3.getProps().containsKey("test.key1") &&
          actualConf3.getProps().containsKey("test.key2") &&
          actualConf3.getProps().containsKey("test.key3"));

      // case 4: specify an empty property, ensure all configurations are dumped
      outWriter = new StringWriter();
      Configuration.dumpConfiguration(testConf, "", outWriter);
      jsonStr = outWriter.toString();
      mapper = new ObjectMapper();
      JsonConfiguration jconf4 =
          mapper.readValue(jsonStr, JsonConfiguration.class);
      assertEquals(3, jconf4.getProperties().length);

      outWriter = new StringWriter();
      testConf.writeXml("", outWriter);
      xmlStr = outWriter.toString();
      outWriter.close();
      Configuration actualConf4 = getActualConf(xmlStr);
      assertEquals(3, actualConf4.size());
      assertTrue(actualConf4.getProps().containsKey("test.key1") &&
          actualConf4.getProps().containsKey("test.key2") &&
          actualConf4.getProps().containsKey("test.key3"));
    } finally {
      if(outWriter != null) {
        outWriter.close();
      }
      if(out != null) {
        out.close();
      }
    }
  }

  public void testDumpConfiguration() throws IOException {
    StringWriter outWriter = new StringWriter();
    Configuration.dumpConfiguration(conf, outWriter);
    String jsonStr = outWriter.toString();
    ObjectMapper mapper = new ObjectMapper();
    JsonConfiguration jconf = 
      mapper.readValue(jsonStr, JsonConfiguration.class);
    int defaultLength = jconf.getProperties().length;
    
    // add 3 keys to the existing configuration properties
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.key1", "value1");
    appendProperty("test.key2", "value2",true);
    appendProperty("test.key3", "value3");
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    out.close();
    
    outWriter = new StringWriter();
    Configuration.dumpConfiguration(conf, outWriter);
    jsonStr = outWriter.toString();
    mapper = new ObjectMapper();
    jconf = mapper.readValue(jsonStr, JsonConfiguration.class);
    int length = jconf.getProperties().length;
    // check for consistency in the number of properties parsed in Json format.
    assertEquals(length, defaultLength+3);
    
    //change few keys in another resource file
    out=new BufferedWriter(new FileWriter(CONFIG2));
    startConfig();
    appendProperty("test.key1", "newValue1");
    appendProperty("test.key2", "newValue2");
    endConfig();
    Path fileResource1 = new Path(CONFIG2);
    conf.addResource(fileResource1);
    out.close();
    
    outWriter = new StringWriter();
    Configuration.dumpConfiguration(conf, outWriter);
    jsonStr = outWriter.toString();
    mapper = new ObjectMapper();
    jconf = mapper.readValue(jsonStr, JsonConfiguration.class);
    
    // put the keys and their corresponding attributes into a hashmap for their 
    // efficient retrieval
    HashMap<String,JsonProperty> confDump = new HashMap<String,JsonProperty>();
    for(JsonProperty prop : jconf.getProperties()) {
      confDump.put(prop.getKey(), prop);
    }
    // check if the value and resource of test.key1 is changed
    assertEquals("newValue1", confDump.get("test.key1").getValue());
    assertEquals(false, confDump.get("test.key1").getIsFinal());
    assertEquals(fileResource1.toString(),
        confDump.get("test.key1").getResource());
    // check if final parameter test.key2 is not changed, since it is first 
    // loaded as final parameter
    assertEquals("value2", confDump.get("test.key2").getValue());
    assertEquals(true, confDump.get("test.key2").getIsFinal());
    assertEquals(fileResource.toString(),
        confDump.get("test.key2").getResource());
    // check for other keys which are not modified later
    assertEquals("value3", confDump.get("test.key3").getValue());
    assertEquals(false, confDump.get("test.key3").getIsFinal());
    assertEquals(fileResource.toString(),
        confDump.get("test.key3").getResource());
    // check for resource to be "Unknown" for keys which are loaded using 'set' 
    // and expansion of properties
    conf.set("test.key4", "value4");
    conf.set("test.key5", "value5");
    conf.set("test.key6", "${test.key5}");
    outWriter = new StringWriter();
    Configuration.dumpConfiguration(conf, outWriter);
    jsonStr = outWriter.toString();
    mapper = new ObjectMapper();
    jconf = mapper.readValue(jsonStr, JsonConfiguration.class);
    confDump = new HashMap<String, JsonProperty>();
    for(JsonProperty prop : jconf.getProperties()) {
      confDump.put(prop.getKey(), prop);
    }
    assertEquals("value5",confDump.get("test.key6").getValue());
    assertEquals("programatically", confDump.get("test.key4").getResource());
    outWriter.close();
  }
  
  public void testDumpConfiguratioWithoutDefaults() throws IOException {
    // check for case when default resources are not loaded
    Configuration config = new Configuration(false);
    StringWriter outWriter = new StringWriter();
    Configuration.dumpConfiguration(config, outWriter);
    String jsonStr = outWriter.toString();
    ObjectMapper mapper = new ObjectMapper();
    JsonConfiguration jconf = 
      mapper.readValue(jsonStr, JsonConfiguration.class);
    
    //ensure that no properties are loaded.
    assertEquals(0, jconf.getProperties().length);
    
    // add 2 keys
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.key1", "value1");
    appendProperty("test.key2", "value2",true);
    endConfig();
    Path fileResource = new Path(CONFIG);
    config.addResource(fileResource);
    out.close();
    
    outWriter = new StringWriter();
    Configuration.dumpConfiguration(config, outWriter);
    jsonStr = outWriter.toString();
    mapper = new ObjectMapper();
    jconf = mapper.readValue(jsonStr, JsonConfiguration.class);
    
    HashMap<String, JsonProperty>confDump = new HashMap<String, JsonProperty>();
    for (JsonProperty prop : jconf.getProperties()) {
      confDump.put(prop.getKey(), prop);
    }
    //ensure only 2 keys are loaded
    assertEquals(2,jconf.getProperties().length);
    //ensure the values are consistent
    assertEquals(confDump.get("test.key1").getValue(),"value1");
    assertEquals(confDump.get("test.key2").getValue(),"value2");
    //check the final tag
    assertEquals(false, confDump.get("test.key1").getIsFinal());
    assertEquals(true, confDump.get("test.key2").getIsFinal());
    //check the resource for each property
    for (JsonProperty prop : jconf.getProperties()) {
      assertEquals(fileResource.toString(),prop.getResource());
    }
  }

  public void testDumpSensitiveProperty() throws IOException {
    final String myPassword = "ThisIsMyPassword";
    Configuration testConf = new Configuration(false);
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.password", myPassword);
    endConfig();
    Path fileResource = new Path(CONFIG);
    testConf.addResource(fileResource);

    try (StringWriter outWriter = new StringWriter()) {
      testConf.set(SENSITIVE_CONFIG_KEYS, "password$");
      Configuration.dumpConfiguration(testConf, "test.password", outWriter);
      assertFalse(outWriter.toString().contains(myPassword));
    }
  }

  public void testDumpSensitiveConfiguration() throws IOException {
    final String myPassword = "ThisIsMyPassword";
    Configuration testConf = new Configuration(false);
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("test.password", myPassword);
    endConfig();
    Path fileResource = new Path(CONFIG);
    testConf.addResource(fileResource);

    try (StringWriter outWriter = new StringWriter()) {
      testConf.set(SENSITIVE_CONFIG_KEYS, "password$");
      Configuration.dumpConfiguration(testConf, outWriter);
      assertFalse(outWriter.toString().contains(myPassword));
    }
  }

  public void testGetValByRegex() {
    Configuration conf = new Configuration();
    String key1 = "t.abc.key1";
    String key2 = "t.abc.key2";
    String key3 = "tt.abc.key3";
    String key4 = "t.abc.ey3";
    conf.set(key1, "value1");
    conf.set(key2, "value2");
    conf.set(key3, "value3");
    conf.set(key4, "value3");

    Map<String,String> res = conf.getValByRegex("^t\\..*\\.key\\d");
    assertTrue("Conf didn't get key " + key1, res.containsKey(key1));
    assertTrue("Conf didn't get key " + key2, res.containsKey(key2));
    assertTrue("Picked out wrong key " + key3, !res.containsKey(key3));
    assertTrue("Picked out wrong key " + key4, !res.containsKey(key4));
  }
  
  public void testSettingValueNull() throws Exception {
    Configuration config = new Configuration();
    try {
      config.set("testClassName", null);
      fail("Should throw an IllegalArgumentException exception ");
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
      assertEquals(e.getMessage(),
          "The value of property testClassName must not be null");
    }
  }

  public void testSettingKeyNull() throws Exception {
    Configuration config = new Configuration();
    try {
      config.set(null, "test");
      fail("Should throw an IllegalArgumentException exception ");
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
      assertEquals(e.getMessage(), "Property name must not be null");
    }
  }

  public void testInvalidSubstitutation() {
    final Configuration configuration = new Configuration(false);

    // 2-var loops
    //
    final String key = "test.random.key";
    for (String keyExpression : Arrays.asList(
    "${" + key + "}",
    "foo${" + key + "}",
    "foo${" + key + "}bar",
    "${" + key + "}bar")) {
      configuration.set(key, keyExpression);
      checkSubDepthException(configuration, key);
    }

    //
    // 3-variable loops
    //

    final String expVal1 = "${test.var2}";
    String testVar1 = "test.var1";
    configuration.set(testVar1, expVal1);
    configuration.set("test.var2", "${test.var3}");
    configuration.set("test.var3", "${test.var1}");
    checkSubDepthException(configuration, testVar1);

    // 3-variable loop with non-empty value prefix/suffix
    //
    final String expVal2 = "foo2${test.var2}bar2";
    configuration.set(testVar1, expVal2);
    configuration.set("test.var2", "foo3${test.var3}bar3");
    configuration.set("test.var3", "foo1${test.var1}bar1");
    checkSubDepthException(configuration, testVar1);
  }

  private static void checkSubDepthException(Configuration configuration,
      String key) {
    try {
      configuration.get(key);
      fail("IllegalStateException depth too large not thrown");
    } catch (IllegalStateException e) {
      assertTrue("Unexpected exception text: " + e,
          e.getMessage().contains("substitution depth"));
    }
  }

  public void testIncompleteSubbing() {
    Configuration configuration = new Configuration(false);
    String key = "test.random.key";
    for (String keyExpression : Arrays.asList(
        "{}",
        "${}",
        "{" + key,
        "${" + key,
        "foo${" + key,
        "foo${" + key + "bar",
        "foo{" + key + "}bar",
        "${" + key + "bar")) {
      configuration.set(key, keyExpression);
      String value = configuration.get(key);
      assertTrue("Unexpected value " + value, value.equals(keyExpression));
    }
  }

  public void testGetClassByNameOrNull() throws Exception {
   Configuration config = new Configuration();
   Class<?> clazz = config.getClassByNameOrNull("java.lang.Object");
   assertNotNull(clazz);
  }

  public void testGetFinalParameters() throws Exception {
    out=new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    declareProperty("my.var", "x", "x", true);
    endConfig();
    Path fileResource = new Path(CONFIG);
    Configuration conf = new Configuration();
    Set<String> finalParameters = conf.getFinalParameters();
    assertFalse("my.var already exists", finalParameters.contains("my.var"));
    conf.addResource(fileResource);
    assertEquals("my.var is undefined", "x", conf.get("my.var"));
    assertFalse("finalparams not copied", finalParameters.contains("my.var"));
    finalParameters = conf.getFinalParameters();
    assertTrue("my.var is not final", finalParameters.contains("my.var"));
  }

  /**
   * A test to check whether this thread goes into infinite loop because of
   * destruction of data structure by resize of Map. This problem was reported
   * by SPARK-2546.
   * @throws Exception
   */
  public void testConcurrentAccesses() throws Exception {
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    declareProperty("some.config", "xyz", "xyz", false);
    endConfig();
    Path fileResource = new Path(CONFIG);
    Configuration conf = new Configuration();
    conf.addResource(fileResource);

    class ConfigModifyThread extends Thread {
      final private Configuration config;
      final private String prefix;

      public ConfigModifyThread(Configuration conf, String prefix) {
        config = conf;
        this.prefix = prefix;
      }

      @Override
      public void run() {
        for (int i = 0; i < 10000; i++) {
          config.set("some.config.value-" + prefix + i, "value");
        }
      }
    }

    ArrayList<ConfigModifyThread> threads = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      threads.add(new ConfigModifyThread(conf, String.valueOf(i)));
    }
    for (Thread t: threads) {
      t.start();
    }
    for (Thread t: threads) {
      t.join();
    }
    // If this test completes without going into infinite loop,
    // it's expected behaviour.
  }

  public void testNullValueProperties() throws Exception {
    Configuration conf = new Configuration();
    conf.setAllowNullValueProperties(true);
    out = new BufferedWriter(new FileWriter(CONFIG));
    startConfig();
    appendProperty("attr", "value", true);
    appendProperty("attr", "", true);
    endConfig();
    Path fileResource = new Path(CONFIG);
    conf.addResource(fileResource);
    assertEquals("value", conf.get("attr"));
  }

  public void testGetClassesShouldReturnDefaultValue() throws Exception {
    Configuration config = new Configuration();
    Class<?>[] classes = 
      config.getClasses("testClassName", Configuration.class);
    assertEquals(
        "Not returning expected number of classes. Number of returned classes ="
            + classes.length, 1, classes.length);
    assertEquals("Not returning the default class Name", Configuration.class,
        classes[0]);
  }

  public void testGetClassesShouldReturnEmptyArray()
      throws Exception {
    Configuration config = new Configuration();
    config.set("testClassName", "");
    Class<?>[] classes = config.getClasses("testClassName", Configuration.class);
    assertEquals(
        "Not returning expected number of classes. Number of returned classes ="
            + classes.length, 0, classes.length);
  }
  
  public void testGetPasswordDeprecatedKeyStored() throws Exception {
    final String oldKey = "test.password.old.key";
    final String newKey = "test.password.new.key";
    final String password = "MyPasswordForDeprecatedKey";

    final File tmpDir = GenericTestUtils.getRandomizedTestDir();
    tmpDir.mkdirs();
    final String ourUrl = new URI(LocalJavaKeyStoreProvider.SCHEME_NAME,
        "file",  new File(tmpDir, "test.jks").toURI().getPath(),
        null).toString();

    conf = new Configuration(false);
    conf.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, ourUrl);
    CredentialProvider provider =
        CredentialProviderFactory.getProviders(conf).get(0);
    provider.createCredentialEntry(oldKey, password.toCharArray());
    provider.flush();

    Configuration.addDeprecation(oldKey, newKey);

    Assert.assertThat(conf.getPassword(newKey),
        CoreMatchers.is(password.toCharArray()));
    Assert.assertThat(conf.getPassword(oldKey),
        CoreMatchers.is(password.toCharArray()));

    FileUtil.fullyDelete(tmpDir);
  }

  public void testGetPasswordByDeprecatedKey() throws Exception {
    final String oldKey = "test.password.old.key";
    final String newKey = "test.password.new.key";
    final String password = "MyPasswordForDeprecatedKey";

    final File tmpDir = GenericTestUtils.getRandomizedTestDir();
    tmpDir.mkdirs();
    final String ourUrl = new URI(LocalJavaKeyStoreProvider.SCHEME_NAME,
        "file",  new File(tmpDir, "test.jks").toURI().getPath(),
        null).toString();

    conf = new Configuration(false);
    conf.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, ourUrl);
    CredentialProvider provider =
        CredentialProviderFactory.getProviders(conf).get(0);
    provider.createCredentialEntry(newKey, password.toCharArray());
    provider.flush();

    Configuration.addDeprecation(oldKey, newKey);

    Assert.assertThat(conf.getPassword(newKey),
        CoreMatchers.is(password.toCharArray()));
    Assert.assertThat(conf.getPassword(oldKey),
        CoreMatchers.is(password.toCharArray()));

    FileUtil.fullyDelete(tmpDir);
  }

  public void testGettingPropertiesWithPrefix() throws Exception {
    Configuration conf = new Configuration();
    for (int i = 0; i < 10; i++) {
      conf.set("prefix." + "name" + i, "value" + i);
    }
    conf.set("different.prefix" + ".name", "value");
    Map<String, String> prefixedProps = conf.getPropsWithPrefix("prefix.");
    assertEquals(prefixedProps.size(), 10);
    for (int i = 0; i < 10; i++) {
      assertEquals("value" + i, prefixedProps.get("name" + i));
    }

    // Repeat test with variable substitution
    conf.set("foo", "bar");
    for (int i = 0; i < 10; i++) {
      conf.set("subprefix." + "subname" + i, "value_${foo}" + i);
    }
    prefixedProps = conf.getPropsWithPrefix("subprefix.");
    assertEquals(prefixedProps.size(), 10);
    for (int i = 0; i < 10; i++) {
      assertEquals("value_bar" + i, prefixedProps.get("subname" + i));
    }
    // test call with no properties for a given prefix
    prefixedProps = conf.getPropsWithPrefix("none");
    assertNotNull(prefixedProps.isEmpty());
    assertTrue(prefixedProps.isEmpty());
  }

  public static void main(String[] argv) throws Exception {
    junit.textui.TestRunner.main(new String[]{
      TestConfiguration.class.getName()
    });
  }
}
