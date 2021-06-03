FROM quay.io/strimzi/kafka:0.23.0-kafka-2.8.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
