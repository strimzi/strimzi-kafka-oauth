FROM jboss/keycloak

COPY config/keycloak.server.keystore.p12 /opt/jboss/keycloak/standalone/configuration/
COPY keycloak-ssl.cli /opt/jboss/keycloak/

RUN cd keycloak && \
    bin/jboss-cli.sh --file=keycloak-ssl.cli && \
    rm -rf standalone/configuration/standalone_xml_history/current

