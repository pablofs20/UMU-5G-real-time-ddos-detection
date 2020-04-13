package org.um.utils

import java.io.FileWriter

import org.apache.spark.TaskContext
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.um.processor.ProcessorPool
import org.um.streaming.Consumer

class ConversationToFile extends Consumer{
    def run() {
        // init Consumer
        initConsumer()

        // Start reading messages from Kafka and get DStream
        val stream = KafkaUtils.createDirectStream[String, String](streaming, LocationStrategies.PreferConsistent,
            ConsumerStrategies.Subscribe[String, String](Array(conversations_topic), kafkaParams))

        stream.foreachRDD( rdd =>{
            rdd.foreach(record =>{
                println("hola" + record)
                val fw = new FileWriter("test.csv", true)
                fw.write(record.value())
                fw.write("\n")
                fw.close()
            })
        })

        streaming.start()
        streaming.awaitTermination()
    }

}

object ConversationToFile {
    def main(args: Array[String]): Unit = {
        new ConversationToFile().run()
    }
}
