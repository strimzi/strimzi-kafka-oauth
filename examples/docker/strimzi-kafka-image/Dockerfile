FROM quay.io/strimzi/kafka:latest-kafka-4.4.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
