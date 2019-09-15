Kafka using OAuth2 for authentication
=====================================

This projects build and runs a docker container running a single-broker Kafka cluster configured with OAuth2 support.


### Building

Copy resources to prepare the docker-compose project by running:

    mvn clean package
    

### Preparing

This demo is configured to use an open-source OAuth2 compliant Keycloak SSO server. It is important that all Keycloak clients access
the OAuth2 endpoints using the same url schema, host and port.
 
First, determine your machine's local network IP address, and add keycloak entry to your `/etc/host`.

You can use `ifconfig` utility. On macOS for example you can run:

    ifconfig en0 | grep 'inet ' | awk '{print $2}'

Then, add keycloak entry to your `/etc/hosts` file 

    <YOUR_IP_ADDRESS>    keycloak


### Running without SSL
    
From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

Kafka broker should be available on localhost:9092. It connects to Keycloak using `http://keycloak:8080`


### Running with SSL

You may want to delete any previous instances by using:

    docker rm -f kafka zookeeper

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build
     
Kafka broker should be available on localhost:9092. It connects to Keycloak using `https://keycloak:8443`


