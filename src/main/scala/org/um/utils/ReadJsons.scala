package org.um.utils

import java.util.Properties
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.io.Source

object ReadJsons extends App{
    def getKey(json: String): String ={
        val socket = JsonMonitorizationParser.getIPsPorts(json)
        var key = 1
        socket.forEach(s =>{
            key *= s.hashCode
        })

        key.abs.toString
    }

    val topic = "flow_info"

    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("acks", "all")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String,String](props)

    val filename = "/home/stucky/Descargas/salida_flowinfo.txt"
    val file = Source.fromFile(filename)
    val cont = file.mkString.split("(?m)^\\s*$")
    file.close()

    cont.foreach(json =>{
        producer.send(new ProducerRecord[String,String](topic,ReadJsons.getKey(json),json))
        Thread.sleep(50)

    })
}
