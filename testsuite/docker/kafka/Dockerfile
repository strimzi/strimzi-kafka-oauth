FROM quay.io/strimzi/kafka:0.48.0-kafka-4.1.0

USER root
RUN rm -rf /opt/kafka/libs/bcpkix* /opt/kafka/libs/bcprov* /opt/kafka/libs/keycloak*
USER kafka
