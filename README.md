# Real Time attack detection  in 5G


## Configuration
A configuration file can be found in *config/config.properties*. This file includes relevant properties for
[Apache Kafka](https://kafka.apache.org/) and [Apache Spark Streaming](https://spark.apache.org/streaming/).

Logs are generated using *log4j*. Another schema can be used modifying *src/resources/log4j.properties* 

To configure the machine learning processor, python requeriments can be found in *env/python_req.txt*.
To get this to work is recommended to create a [Conda](https://docs.conda.io/en/latest/) enviroment:
```
$ conda create --name <env> --file python_req.txt
```
And later, activate it:
```
$ conda activate <env>
```  

## Compilation & Exectution
This software uses [Apache Maven](https://maven.apache.org/) for compilation. Please refer to the Maven documentation
 to install this software in your machine.
 
 Launch Conversation Processor:
```
$ mvn package exec:java -Dexec.mainClass=org.um.streaming.TraceConsumer  
```

To launch the ml processor, make sure you have activate the conda enviroment. Then, run:
```
$ sh runML.sh
```