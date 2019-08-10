FROM jboss/keycloak:latest

RUN mkdir /opt/jboss/realms
COPY realms/* /opt/jboss/realms/
COPY start.sh /opt/jboss/

ENTRYPOINT []
CMD ["/bin/bash", "/opt/jboss/start.sh"]