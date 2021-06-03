FROM quay.io/strimzi/kafka:0.23.0-kafka-2.8.0

COPY start.sh /opt/kafka/
COPY simple_zk_config.sh /opt/kafka/

USER root
RUN chmod +x /opt/kafka/start.sh /opt/kafka/simple_zk_config.sh
USER kafka

CMD ["/bin/bash", "/opt/kafka/start.sh"]