FROM quay.io/strimzi/kafka:latest-kafka-4.3.1

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
