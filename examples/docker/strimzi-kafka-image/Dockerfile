FROM quay.io/strimzi/kafka:0.47.0-kafka-3.9.1

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
