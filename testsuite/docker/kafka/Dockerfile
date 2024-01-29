FROM quay.io/strimzi/kafka:0.39.0-kafka-3.6.1

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
