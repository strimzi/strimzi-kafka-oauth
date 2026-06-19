FROM quay.io/strimzi/kafka:1.0.1-kafka-4.2.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
