FROM quay.io/keycloak/keycloak:13.0.1

RUN mkdir /opt/jboss/realms
COPY realms/* /opt/jboss/realms/
COPY config/* /opt/jboss/
COPY start.sh /opt/jboss/

ENTRYPOINT []
CMD ["/bin/bash", "/opt/jboss/start.sh"]