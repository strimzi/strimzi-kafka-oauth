FROM strimzi/kafka:latest-kafka-2.3.0

COPY libs/* /opt/kafka/libs/
COPY config/* /opt/kafka/config/
COPY start.sh /opt/kafka/
COPY simple_kafka_config.sh /opt/kafka/

USER root
RUN chmod +x /opt/kafka/start.sh /opt/kafka/simple_kafka_config.sh
USER kafka

CMD ["/bin/bash", "/opt/kafka/start.sh"]