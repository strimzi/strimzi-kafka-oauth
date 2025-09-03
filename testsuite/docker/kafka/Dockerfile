FROM quay.io/strimzi/kafka:0.47.0-kafka-3.9.1

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
