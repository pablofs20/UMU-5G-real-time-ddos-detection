from pyspark import SparkContext, SparkConf
from pyspark.streaming import StreamingContext
from pyspark.streaming.kafka import KafkaUtils
import json
from pyhocon import ConfigFactory
from ml.mlModel import MLModel

CONF_FILE = 'src/main/resources/config.properties'


def send_to_kafka(rdd):
    if rdd is None:
        return

    for i in rdd:
        print(str(i))


def check_attack_transformation(broadcast, rdd):
    ls = list(rdd)
    if rdd is None or len(ls) == 0:
        return []

    return broadcast.value.check_attack(ls)


def run():
    # Parse conf
    conf_hocon = ConfigFactory.parse_file(CONF_FILE)

    batch_duration = conf_hocon.get('ml.batch_duration')
    topic = [conf_hocon.get_string('conversations_topic')]
    kafka_address = conf_hocon.get_string('kafka_address')
    group_id = conf_hocon.get('conversation_group_id')
    app_name = conf_hocon.get('ml.app_name')
    master = conf_hocon.get_string('ml.master')
    backpressure = conf_hocon.get('ml.backpressure')
    training_file = conf_hocon.get_string('ml.training_file')

    spark_conf = SparkConf().setMaster(master).setAppName(app_name) \
        .set('spark.streaming.backpressure.enabled', backpressure)

    sc = SparkContext(conf=spark_conf)
    sc.setLogLevel("ERROR")

    ml = MLModel(training_data=training_file)
    ml.start()
    broadcast = sc.broadcast(ml)

    scc = StreamingContext(sc, batch_duration)
    stream = KafkaUtils.createDirectStream(scc, topic, {'bootstrap.servers': kafka_address,
                                                        'group.id': group_id})

    stream.repartition(10)

    stream.map(lambda x: x[1]) \
        .map(json.loads) \
        .mapPartitions(lambda rdd: check_attack_transformation(broadcast, rdd)) \
        .foreachRDD(lambda rdd: rdd.foreachPartition(send_to_kafka))

    scc.start()
    scc.awaitTermination()


if __name__ == "__main__":
    run()
