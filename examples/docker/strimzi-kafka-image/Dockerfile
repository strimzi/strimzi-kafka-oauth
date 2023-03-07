FROM quay.io/strimzi/kafka:0.33.2-kafka-3.4.0

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
