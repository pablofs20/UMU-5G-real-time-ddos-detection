package org.um.utils

import java.io.FileWriter

import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.um.streaming.Consumer

class TracesToFile extends Consumer{
    def run() {
        // init Consumer
        initConsumer()

        // Start reading messages from Kafka and get DStream
        val stream = KafkaUtils.createDirectStream[String, String](streaming, LocationStrategies.PreferConsistent,
            ConsumerStrategies.Subscribe[String, String](Array(traces_topic), kafkaParams))

        stream.foreachRDD( rdd =>{
            rdd.foreach(record =>{
                println("hola" + record)
                val fw = new FileWriter("test.txt", true)
                fw.write(record.value())
                fw.write("\n")
                fw.close()
            })
        })

        streaming.start()
        streaming.awaitTermination()
    }

}

object TracesToFile {
    def main(args: Array[String]): Unit = {
        new TracesToFile().run()
    }
}
