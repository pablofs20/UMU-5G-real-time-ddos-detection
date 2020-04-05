package org.um.streaming

import java.util.Properties

import com.typesafe.scalalogging.Logger
import org.apache.spark.SparkConf
import org.apache.spark.streaming._
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.api.java.JavaStreamingContext

object Consumer {
    private val CONFIG_FILE = "config.properties"
}

abstract class Consumer {
    protected val logger : Logger = Logger(getClass)
    protected var streaming : JavaStreamingContext = _
    protected var kafkaParams : Map[String, Object] = _
    protected var traces_topic, conversations_topic  : String = _

    def initConsumer(): Unit = {
        // Read from config.properties
        val props = new Properties()
        val resourceStream = getClass.getClassLoader.getResourceAsStream(Consumer.CONFIG_FILE)
        props.load(resourceStream)
        resourceStream.close()

        //Parse properties
        val name = props.getProperty("app_name")
        val batch_duration = props.getProperty("batch_duration")
        val master = props.getProperty("master")
        val backpressure = props.getProperty("backpressure")

        val conf = new SparkConf().setAppName(name).setMaster(master)
            .set("spark.streaming.backpressure.enabled",backpressure)

        logger.info("Staring Streaming Context with properties: name -> " + name + ", batch_duration -> " + batch_duration
        + ", master -> " + master + ", backpressure-> " + backpressure)
        streaming = new JavaStreamingContext(conf, Seconds(batch_duration.toLong))

        // Parse Kafka params
        val kafkaAddress = props.getProperty("kafka_address")
        val consumerGroup = props.getProperty("traces_group_id")
        traces_topic = props.getProperty("traces_topic")
        conversations_topic = props.getProperty("conversations_topic")

        logger.info("Kafka parameters: address -> " + kafkaAddress + ", ConsumerGroup -> " + consumerGroup)

        kafkaParams = Map[String, Object](
            "bootstrap.servers" -> kafkaAddress ,
            "key.deserializer" -> classOf[StringDeserializer],
            "value.deserializer" -> classOf[StringDeserializer],
            "group.id" -> consumerGroup,
            "auto.offset.reset" -> "latest",
            "enable.auto.commit" -> (false: java.lang.Boolean)
        )

    }
}


