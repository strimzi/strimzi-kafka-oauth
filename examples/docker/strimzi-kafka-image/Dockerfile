FROM quay.io/strimzi/kafka:0.50.1-kafka-4.1.1

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
