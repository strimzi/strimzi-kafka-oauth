FROM quay.io/strimzi/kafka:0.21.1-kafka-2.6.0

COPY libs/* /opt/kafka/libs/strimzi/
COPY config/* /opt/kafka/config/
COPY *.sh /opt/kafka/
COPY certificates/*.p12 /tmp/kafka/

USER root
RUN chmod +x /opt/kafka/*.sh
USER kafka

CMD ["/bin/bash", "/opt/kafka/start.sh"]