FROM quay.io/strimzi/kafka:0.33.1-kafka-3.3.2

COPY target/libs/* /opt/kafka/libs/oauth/
ENV CLASSPATH /opt/kafka/libs/oauth/*
