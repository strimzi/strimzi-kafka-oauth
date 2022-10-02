FROM quay.io/keycloak/keycloak:19.0.2-legacy

RUN mkdir /opt/jboss/realms
COPY realms/* /opt/jboss/realms/
COPY config/* /opt/jboss/
COPY start.sh /opt/jboss/

ENTRYPOINT []
CMD ["/bin/bash", "/opt/jboss/start.sh"]