FROM quay.io/strimzi/kafka:0.23.0-kafka-2.8.0

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
