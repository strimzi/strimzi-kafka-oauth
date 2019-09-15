Demo services
=============

This module provides docker containers for the demo. It includes Keycloak, realm import service for Keycloak, and a preconfigured Kafka broker.


Building
--------
 
    mvn clean install -f ..


Preparing
---------

Determine your machine's local network IP address, and add keycloak entry to your `/etc/host`.

You can use `ifconfig` utility. On macOS for example you can run:

    ifconfig en0 | grep 'inet ' | awk '{print $2}'

Then, add keycloak entry to your `/etc/hosts` file 

    <YOUR_IP_ADDRESS>    keycloak

That's needed, because Kafka brokers and Kafka clients connecting to Keycloak have to use the same hostname to ensure 
compatibility of generated access tokens.

When client connects to Kafka broker running inside docker image, the broker will redirect the client to: kafka:9292.
For that reason you also have to put the following line in your `/etc/hosts`:

    127.0.0.1            kafka


Running 
-------
    
All the following docker-compose commands should be run from this directory.

You may want to remove any old container to start clean:

    docker rm -f kafka zookeeper keycloak


Running without SSL
-------------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml -f keycloak/compose.yml -f keycloak-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

    docker-compose -f keycloak/compose.yml up

    docker-compose -f compose.yml -f keycloak-import/compose.yml up --build


Running with SSL
----------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml -f keycloak/compose-ssl.yml -f keycloak-import/compose-ssl.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build 

    docker-compose -f keycloak/compose-ssl.yml up

    docker-compose -f compose.yml -f keycloak-import/compose-ssl.yml up --build

See [examples README](../README.md) for more information.
