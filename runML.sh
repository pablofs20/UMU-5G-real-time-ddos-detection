zip -j bin/req.zip src/main/python/ml/mlModel.py src/main/python/utils/JsonConversationParser.py\
&& export PYTHONPATH="$PWD/src/main/python" \
&& spark-submit --py-files bin/req.zip --jars bin/spark-streaming-kafka-0-8-assembly_2.11-2.4.5.jar src/main/python/streaming/ConversationConsumer.py