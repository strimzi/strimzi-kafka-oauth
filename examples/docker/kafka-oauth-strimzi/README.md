Kafka using OAuth2 for authentication
=====================================

This projects build and runs a docker container running a single-broker Kafka cluster configured with OAuth2 support.


Building
--------

Copy resources to prepare the docker-compose project by running:

1. Make sure to reference the desired version for the dependencies. If in doubt you may just reference the latest version from the [official public maven repository](https://mvnrepository.com/artifact/io.strimzi/kafka-oauth-client). For example, add the line `<version>0.15.0</version>` the [pom.xlm](./kafka/pom.xlm) in the [kafka dir](./kafka) for each dependency to go with version 0.15.0.:
   ```xml
   ...
   <configuration>
    <artifactItems>
        <artifactItem>
            <groupId>io.strimzi</groupId>
            <artifactId>kafka-oauth-client</artifactId>
            <version>0.15.0</version> <!-- Add new line with dependeny version. Use the version you found suitable -->
        </artifactItem>
        <!-- Configure other artifact items similarly, specifying versions -->
   ...
   ```

3. Build
   ```sh
   mvn clean package
   ``` 

Preparing
---------

This demo comes with several configurations using three different OAuth2 authorization servers - Keycloak, Hydra, and Spring Authorization Server. 
It is important that all clients access the OAuth2 endpoints using the same url schema, host and port.
 
First, determine your machine's local network IP address, and add keycloak / hydra / spring entry to your `/etc/host`.

You can use `ifconfig` utility. On macOS for example you can run:

    ifconfig en0 | grep 'inet ' | awk '{print $2}'

Then, add keycloak / hydra / spring entry to your `/etc/hosts` file 

    <YOUR_IP_ADDRESS>    keycloak
    <YOUR_IP_ADDRESS>    hydra
    <YOUR_IP_ADDRESS>    spring

Usually you can simply use `localhost` instead of <YOUR_IP_ADDRESS>.

Before each run you may want to delete any previous instances by using:

    docker rm -f kafka zookeeper


Running using Keycloak without SSL
----------------------------------
    
From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

Kafka broker should be available on localhost:9092. It connects to Keycloak using `http://keycloak:8080`


Running using Keycloak with SSL
-------------------------------

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build
     
Kafka broker should be available on localhost:9092. It connects to Keycloak using `https://keycloak:8443`


Running using Hydra with SSL and opaque tokens
----------------------------------------------

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra.yml up --build
     
Kafka broker should be available on localhost:9092. It connects to Hydra using `https://hydra:4444`


Running using Hydra with SSL and JWT tokens
----------------------------------------------

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra-jwt.yml up --build
     
Kafka broker should be available on localhost:9092. It connects to Hydra using `https://hydra:4444`


Running using Spring with opaque tokens
---------------------------------------

From `docker` directory run:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring.yml up --build
    
Kafka broker should be avaliable on localhost:9092. It connects to Spring Authorization Server using `http://spring:8080`
