Environment and necessary tools: Linux 18.04, java 1.8, maven, protobuf 2.5(must be this version)

In addition to this repo, you should also download the newClient source code: https://github.com/ljx0320/591_newClient

Step 1: compile the hadoop project. This may take around 20 minutes:
mvn package -Pdist -Pdoc -Psrc -DskipTests

Step 2: edit the hdfs-site.xml and core-site.xml in etc/hadoop to configure the hdfs. Below is the sample:
hdfs-site.xml:

<configuration>
    <property>
        <name>dfs.replication</name>
        <value>1</value>
    </property>
    <property>
        <name>dfs.namenode.name.dir</name>
        <value>file:///home/hadoop2/Downloads/hadoop-2.10.0-src/hadoop-dist/target/hadoop-2.10.0/hdfs/namenode</value>
    </property>
    <property>
        <name>dfs.datanode.data.dir</name>
        <value>file:///home/hadoop2/Downloads/hadoop-2.10.0-src/hadoop-dist/target/hadoop-2.10.0/hdfs/datanode</value>
    </property>
</configuration>

core-site.xml:

<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://localhost:9000</value>
    </property>
</configuration>

Step 3: format the dfs:
bin/hadoop namenode -format

Step 4: refer to README for newClient: https://github.com/ljx0320/591_newClient/blob/master/README.md

Step 5: After you run the client proxy up, run name node and data node:
sbin/hadoop-daemon.sh --script bin/hdfs start namenode
sbin/hadoop-daemon.sh --script bin/hdfs start datanode

Step 6: Now you are all set to send messages: https://hadoop.apache.org/docs/r2.10.0/hadoop-project-dist/hadoop-common/FileSystemShell.html
