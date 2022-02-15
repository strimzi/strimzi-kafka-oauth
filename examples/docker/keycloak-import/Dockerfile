FROM quay.io/keycloak/keycloak:15.0.0

RUN mkdir /opt/jboss/realms
COPY realms/* /opt/jboss/realms/
COPY config/* /opt/jboss/
COPY start.sh /opt/jboss/

ENTRYPOINT []
CMD ["/bin/bash", "/opt/jboss/start.sh"]