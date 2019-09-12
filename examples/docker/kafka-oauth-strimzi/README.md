Kafka using OAuth2 for authentication
=====================================

This projects build and runs a docker container running a single-broker Kafka cluster configured with OAuth2 support.


### Building

Copy resources to prepare the docker-compose project by running:

    mvn clean package
    

### Preparing

This demo is configured to use an open-source OAuth2 compliant Keycloak SSO server. It is important that all Keycloak clients access
the OAuth2 endpoints using the same url schema, host and port.
 
Therefore, before continuing, first determine the local IP address of your machine, and set the IP as environment variable:

    export KEYCLOAK_IP=<YOUR_IP_ADDRESS>

For example, on macOS:

    export KEYCLOAK_IP=$(ifconfig en0 | grep 'inet ' | awk '{print $2}')


### Running without SSL
    
From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

Kafka broker should be available on localhost:9092. It connects to Keycloak using `http://${KEYCLOAK_IP}:8080`


### Running with SSL

You may want to delete any previous instances by using:

    docker rm -f kafka zookeeper

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build
     
Kafka broker should be available on localhost:9092. It connects to Keycloak using `https://${KEYCLOAK_IP}:8443`

