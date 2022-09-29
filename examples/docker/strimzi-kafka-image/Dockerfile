FROM quay.io/strimzi/kafka:0.31.1-kafka-3.2.3

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
