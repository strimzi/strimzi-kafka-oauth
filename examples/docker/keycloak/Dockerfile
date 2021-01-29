FROM quay.io/keycloak/keycloak:12.0.2

COPY certificates/keycloak.server.keystore.p12 /opt/jboss/keycloak/standalone/configuration/
COPY keycloak-ssl.cli /opt/jboss/keycloak/

RUN cd /opt/jboss/keycloak && \
    bin/jboss-cli.sh --file=keycloak-ssl.cli && \
    rm -rf standalone/configuration/standalone_xml_history/current

