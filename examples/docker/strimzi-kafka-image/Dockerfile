FROM quay.io/strimzi/kafka:0.47.0-kafka-4.0.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
