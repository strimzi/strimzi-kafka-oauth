FROM quay.io/strimzi/kafka:0.22.1-kafka-2.7.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
