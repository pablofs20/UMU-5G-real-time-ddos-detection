# Real Time attack detection  in 5G


## Configuration
A configuration file can be found in *config/config.properties*. This file includes relevant properties for
[Apache Kafka](https://kafka.apache.org/) and [Apache Spark Streaming](https://spark.apache.org/streaming/).

To configure the machine learning processor, python requeriments can be found in *env/python_req.txt*.
To get this to work is recommended to create a [Conda](https://docs.conda.io/en/latest/) enviroment:
```
$ conda create --name <env> --file python_req.txt
```
And later, activate it:
```
$ conda activate <env>
```  

## Compilation & Execution
This project uses [SBT](https://www.scala-sbt.org/) for compilation. Please refer to the SBT [documentation](https://www.scala-sbt.org/1.x/docs/)
 to install this software in your machine.
 
 To compile and launch the conversation processor, run:
```
$ sbt compile
$ sbt run
```

To launch the ml processor, make sure you have activated the conda enviroment. Then, run:
```
$ sh runML.sh
```
