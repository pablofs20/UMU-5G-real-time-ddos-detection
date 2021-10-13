# Real Time attack detection  in 5G


## Configuration
For the monitoring sensor, a configuration file can be found in *5G-monitoring-sensor/config/sniffer.conf*. Modify this file in order to change the interface name, specify a BPF filter, and configure the Kafka connection details.

For the conversations and ML processors, a configuration file can be found in *config/config.properties*. This file includes relevant properties for
[Apache Kafka](https://kafka.apache.org/) and [Apache Spark Streaming](https://spark.apache.org/streaming/).

In addition, to fully configure the ML processor, Python requeriments can be found in *env/python_req.txt*.
To get this to work, it is recommended to create a [Conda](https://docs.conda.io/en/latest/) enviroment:
```
$ conda create --name <env> --file python_req.txt
```
And later, activate it:
```
$ conda activate <env>
```  

## Compilation & Execution
This project uses [SBT](https://www.scala-sbt.org/) for compilation. Please refer to the SBT [documentation](https://www.scala-sbt.org/1.x/docs/)
 to install this software in your machine. You also need to ensure that *gcc*, as well as *libpcap-dev* and *librdkafka-dev* packages are correctly installed on your system for the monitoring sensor to compile successfully.
 
 To compile and launch the monitoring sensor, run:
 ```
$ cd 5G-monitoring-sensor
$ gcc main.c sniffer.c -o sensor -lpcap -lrdkafka
$ ./sensor
```
 
 To compile and launch the conversation processor, run:
```
$ sbt compile
$ sbt run
```

To launch the ML processor, make sure you have activated the conda enviroment. Then, run:
```
$ sh runML.sh
```
