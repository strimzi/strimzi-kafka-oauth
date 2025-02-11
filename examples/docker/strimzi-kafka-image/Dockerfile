FROM quay.io/strimzi/kafka:0.45.0-kafka-3.9.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
