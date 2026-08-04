FROM quay.io/strimzi/kafka:latest-kafka-4.3.1

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
