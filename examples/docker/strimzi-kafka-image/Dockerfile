FROM quay.io/strimzi/kafka:0.28.0-kafka-3.1.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
