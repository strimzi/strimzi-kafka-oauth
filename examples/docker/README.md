Demo services
=============

This module provides docker containers for the demo. It includes Keycloak, and a preconfigured Kafka broker.

Alternative option is to use included 'hydra' or 'spring' project as an authorization server.


Building
--------

    # build the whole project to make sure the latest code is packaged into docker images
    mvn clean install -f ../..
    
    # prepare files for docker-compose builds
    mvn clean install


Preparing
---------

Make sure that the following ports on your host machine are free: 9092, 2181 (Kafka), 8080 (Spring or Keycloak), 8443 (Keycloak), 4444, 4445 (Hydra).

Then, you have to add some entries to your `/etc/hosts` file:

    127.0.0.1            keycloak
    127.0.0.1            hydra
    127.0.0.1            kafka
    127.0.0.1            spring

That's needed for host resolution, because Kafka brokers and Kafka clients connecting to Keycloak / Hydra have to use the 
same hostname to ensure compatibility of generated access tokens.

Also, when Kafka client connects to Kafka broker running inside docker image, the broker will redirect the client to: kafka:9092.


Running 
-------
    
All the following docker-compose commands should be run from this directory.

You may want to remove any old containers to start clean:

    docker rm -f kafka zookeeper keycloak spring


Running with Keycloak
---------------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml -f keycloak/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

    docker-compose -f compose.yml -f keycloak/compose.yml up


Running with Keycloak using SSL
-------------------------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml -f keycloak/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build 

    docker-compose -f compose.yml -f keycloak/compose.yml up


Running with Hydra using SSL and opaque tokens
----------------------------------------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra.yml -f hydra/compose.yml -f hydra-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra.yml up --build 

    docker-compose -f compose.yml -f hydra/compose.yml up

    docker-compose -f compose.yml -f hydra-import/compose.yml up --build


Running with Hydra using SSL and JWT tokens
-------------------------------------------

You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra-jwt.yml -f hydra/compose-with-jwt.yml -f hydra-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra-jwt.yml up --build 

    docker-compose -f compose.yml -f hydra/compose-with-jwt.yml up

    docker-compose -f compose.yml -f hydra-import/compose.yml up --build


Running with Spring using opaque tokens
---------------------------------------

Start spring authorization server first:

    docker-compose -f compose.yml -f spring/compose.yml up

Then start the Kafka broker:
    
    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring.yml up --build


Rebuilding certificates
-----------------------

All the certificates needed by the examples are pre-packaged, but if for some reason you want to regenerate them, here is how you do that.

Subdirectory `certificates` contains Root CA used to sign the server certificates for Keycloak and Hydra.

It also contains a PKCS12 truststore, used by clients that connect to Keycloak or Hydra.

To regenerate Root CA run the following:

    cd certificates
    rm *.crt *.key *.p12
    ./gen-ca.sh
    cd ..

You also have to regenerate keycloak and hydra server certificates otherwise clients won't be able to connect any more.

    cd keycloak/certificates
    rm *.srl *.p12 cert-*
    ./gen-keycloak-certs.sh
    cd ..
    
    cd ../hydra/certificates 
    rm *.srl *.crt *.key *.csr
    ./gen-hydra-certs.sh
    cd ../..

And if CA has changed, then kafka broker certificates have to be regenerated as well:

    cd kafka-oauth-strimzi/kafka/certificates
    rm *.p12
    ./gen-kafka-certs.sh
    cd ../../..

And finally make sure to rebuild the docker module again and re-run `docker-compose` to ensure new keys and certificates are used everywhere.

    mvn clean install


See [examples README](../README.md) for more information.


