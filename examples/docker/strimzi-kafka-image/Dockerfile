FROM quay.io/strimzi/kafka:0.31.0-kafka-3.2.1

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
