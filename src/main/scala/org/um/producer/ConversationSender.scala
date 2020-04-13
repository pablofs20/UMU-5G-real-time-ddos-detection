package org.um.producer

import java.util.Properties

import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.um.processor.Flow

object ConversationSender{
    private val CONFIG_FILE = "config.properties"
}

class ConversationSender {
    private val logger = Logger(getClass)
    private var producer : KafkaProducer[String,String] = _
    private var topic : String = _
    init()


    private def init(): Unit = {
        // Read from config.properties
        val propsFile = new Properties()
        val resourceStream = getClass.getClassLoader.getResourceAsStream(ConversationSender.CONFIG_FILE)
        propsFile.load(resourceStream)
        resourceStream.close()

        //Parse properties
        val kafkaAddress = propsFile.getProperty("kafka_address").replace("\"","")
        topic = propsFile.getProperty("conversations_topic")

        val props = new Properties()
        props.put("bootstrap.servers", kafkaAddress)
        props.put("acks", "all")
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

        producer = new KafkaProducer[String,String](props)
    }

    def sendConversation(flow : Flow): Unit ={
        val conversation = flow.getConversation
        if (conversation == null) return
        logger.info("Conversatione " + conversation.mkString(","))

        //producer.send(new ProducerRecord[String,String](topic,flow.getFlowID.id.toString, flow.getConversation.mkString(",")))
        producer.send(new ProducerRecord[String,String](topic,flow.getFlowID.id.toString, flow.getJson))


    }

}