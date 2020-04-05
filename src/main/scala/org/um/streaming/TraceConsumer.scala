package org.um.streaming

import org.apache.spark.TaskContext
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.um.processor.ProcessorPool


class TraceConsumer extends Consumer {
   def run(){
       // init Consumer
       initConsumer()

       // Start reading messages from Kafka and get DStream
       val stream = KafkaUtils.createDirectStream[String,String](streaming, LocationStrategies.PreferConsistent,
           ConsumerStrategies.Subscribe[String,String](Array(traces_topic),kafkaParams))

       // Transform to key value stream
       val pairs = stream.mapToPair(record => new Tuple2[String, String](record.key(),record.value()))

       // Since the monitoring tool does provide key=null. Establish the according key using socket information.
       // Hash key = IP up + Port up + IP down + Port down
       val newkey = pairs.mapToPair(tuple => {
           val features =  tuple._2.asInstanceOf[String].split(",")

           val key = String.valueOf(features(6).hashCode + features(7).hashCode
               + features(8).hashCode + features(9).hashCode)
           new Tuple2[String,String](key,tuple._2)
       })

       // Group data by key so values with similar key belong to only one partition
       // Spark uses hashpartitioner by default
       val grouped = newkey.groupByKey()


       //For each partition process its network packets using an aggregator
       grouped.foreachRDD(rdd => rdd.foreachPartitionAsync( partition =>
           partition.forEachRemaining(records =>
               // Get aggregator and process records
               ProcessorPool.getProcessor(TaskContext.getPartitionId())
                   .foo(records._2.asInstanceOf[java.lang.Iterable[String]])
           )
       ))

       streaming.start()
       streaming.awaitTermination()

   }
}
