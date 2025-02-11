FROM quay.io/strimzi/kafka:0.45.0-kafka-3.9.0

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
