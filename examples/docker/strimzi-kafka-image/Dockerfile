FROM quay.io/strimzi/kafka:0.39.0-kafka-3.6.1

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
