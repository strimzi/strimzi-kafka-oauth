Keycloak
========

This project builds and runs Keycloak docker container.

### Running without SSL

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak/compose.yml up --build 

You may want to delete any previous instances by using:

    docker rm -f keycloak
    
### Running with SSL

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak/compose-ssl.yml up --build
     

Certificates, keystores, and truststores are pre-generated in `certificates` sub-directory. If you want to regenerate them run:

    cd certificates
    rm c* k*
    ./build-certs.sh


In order for whole SSL demo to work copy the files to where they are needed:

    cp keycloak.server.keystore.p12 ../config/
    cp -t ../../keycloak-import/config/ ca-cert keycloak.client.truststore.p12 
    cp -t ../../kafka-oauth-strimzi/kafka/config/ ca-cert keycloak.client.truststore.p12

