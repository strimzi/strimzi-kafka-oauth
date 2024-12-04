FROM quay.io/strimzi/kafka:0.44.0-kafka-3.8.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
