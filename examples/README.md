Examples / Demo
===============

This directory contains some `docker-compose` based example configurations for setting up `kafka`, and an authorization server (`keycloak`, `hydra` or `spring`). It also contains an example Kafka consumer and Kafka producer clients.

The Keycloak based demo uses a set of preconfigured realms containing configurations for clients and users. The `kafka` image is based on a recent `quay.io/strimzi/kafka` image with included strimzi-kafka-oauth jars produced by the build of the current branch (see: `docker/kafka-oauth-strimzi`).

Some of the demo configurations only set up the OAuth authentication, some also configure a custom authorization, using the authorizer for use with `Keycloak Authorization Services` (`docker/kafka-oauth-strimzi/compose-authz.yaml`).


Preparing
=========

Configuring hostname resolution
-------------------------------

Make sure that the following ports on your host machine are free: 9091, 9092 (Kafka), 8080 (Spring or Keycloak), 8443 (Keycloak), 4444, 4445 (Hydra).

Then, you have to add some entries to your `/etc/hosts` file:

    127.0.0.1            keycloak
    127.0.0.1            hydra
    127.0.0.1            kafka
    127.0.0.1            spring

That's needed for host resolution, because Kafka brokers and Kafka clients connecting to Keycloak / Hydra / Spring have to use the
same hostname to ensure compatibility of generated access tokens.

Also, when Kafka client connects to the Kafka broker running inside a docker image, the broker will redirect the client to: kafka:9092.

Sometimes using `127.0.0.1` as an IP does not work, and you need to use your machine's local network IP address.

You can use `ifconfig` utility. On macOS for example you can run:

    ifconfig en0 | grep 'inet ' | awk '{print $2}'

Then, use the printed IP address in your `/etc/hosts` file instead of `127.0.0.1`.


Rebuilding the project
----------------------

Go to the root directory of the project, and run:

    # build the whole project to make sure the latest code is packaged into docker images
    mvn clean install
    
    # prepare files for docker-compose builds
    mvn clean install -f examples/docker


Rebuilding the certificates
---------------------------

All the certificates needed by the examples are pre-packaged, but if for some reason you want to regenerate them, here is how to do that.

The directory `examples/docker/certificates` contains Root CA used to sign the server certificates for Keycloak and Hydra.

It also contains a PKCS12 truststore, used by clients that connect to Keycloak or Hydra.

To regenerate Root CA run the following:

    cd examples/docker

    cd certificates
    rm *.crt *.key *.p12
    ./gen-ca.sh
    cd ..

You also have to regenerate keycloak and hydra server certificates otherwise clients won't be able to connect any more.

    cd keycloak/certificates
    rm *.p12
    ./gen-keycloak-certs.sh
    cd ..
    
    cd ../hydra/certificates 
    rm *.crt *.key
    ./gen-hydra-certs.sh
    cd ../..

And if CA has changed, then kafka broker certificates have to be regenerated as well:

    cd kafka-oauth-strimzi/kafka/certificates
    rm *.p12
    ./gen-kafka-certs.sh
    cd ../../..

And finally make sure to rebuild the `examples/docker` module again and re-run the `docker-compose` commands to ensure new keys and certificates are used everywhere.

    mvn clean install


Running the containers
======================

Change into `examples/docker` directory before running any of the following examples.

    cd examples/docker

You may want to remove any old containers to start clean:

    docker rm -f kafka keycloak hydra spring


Running with Keycloak
---------------------

You can start up all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml -f keycloak/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

    docker-compose -f compose.yml -f keycloak/compose.yml up


Running with Keycloak using SSL
-------------------------------

You can start up all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml -f keycloak/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-ssl.yml up --build 

    docker-compose -f compose.yml -f keycloak/compose.yml up


Running with Hydra using SSL and opaque tokens
----------------------------------------------

You can start up all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra.yml -f hydra/compose.yml -f hydra-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra.yml up --build 

    docker-compose -f compose.yml -f hydra/compose.yml up

    # Create OAuth client configurations in hydra
    docker-compose -f compose.yml -f hydra-import/compose.yml up --build


Running with Hydra using SSL and JWT tokens
-------------------------------------------

You can start up all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra-jwt.yml -f hydra/compose-with-jwt.yml -f hydra-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-hydra-jwt.yml up --build 

    docker-compose -f compose.yml -f hydra/compose-with-jwt.yml up

    docker-compose -f compose.yml -f hydra-import/compose.yml up --build


Running with Spring using opaque tokens
---------------------------------------

The Spring example requires JDK version 17 or higher.

Before running the `docker-compose` you have to build the example:

    mvn clean install -f spring

Start spring authorization server first:

    docker-compose -f compose.yml -f spring/compose.yml up

Then start the Kafka broker:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring.yml up --build


Running with Spring using JWT tokens
------------------------------------

The Spring example requires JDK version 17 or higher.

Before running the `docker-compose` you have to build the example:

    mvn clean install -f spring

Start spring authorization server first:

    docker-compose -f compose.yml -f spring/compose.yml up

Then start the Kafka broker:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring-jwt.yml up --build


Running the clients
===================

The Kafka Consumer example is located in `examples/consumer` directory, and the Kafka Producer example is located in `exaples/producer` directory.

Both are compiled as part of the full project build as explaned in [Rebuilding the project](#rebuilding-the-project).

The clients are not packaged into a docker image, rather they are supposed to be executed from local Terminal or IDE.

These examples assume that `kafka` and your authorization server (`keycloak`, `hydra`) are running already as described in [Running the containers](#running-the-containers).

Before running the example clients, you need to set additional env variables in your shell in order to configure truststore, and turn off certificate hostname validation on the client:

    export OAUTH_SSL_TRUSTSTORE_LOCATION=../docker/certificates/ca-truststore.p12
    export OAUTH_SSL_TRUSTSTORE_PASSWORD=changeit
    export OAUTH_SSL_TRUSTSTORE_TYPE=pkcs12

To use Keycloak as authorization server set url of Keycloak's demo realm token endpoint, for example if you are using Keycloak with SSL:

    export OAUTH_TOKEN_ENDPOINT_URI=https://keycloak:8443/realms/demo/protocol/openid-connect/token

To use Hydra as authorization server set url of Hydra's token endpoint, for example if you are using Hydra with SSL:

    export OAUTH_TOKEN_ENDPOINT_URI=https://hydra:4444/oauth2/token

If using Hydra with opaque tokens, also set:

    export OAUTH_ACCESS_TOKEN_IS_JWT=false

If using Hydra with JWT tokens, then set:

    export OAUTH_USERNAME_CLAIM=sub


The examples hardcode some of the configuration, specifically the use of `client_credentials` to obtain the `access_token` for authenticating with `kafka`, 
and `client_id` and the `secret` for the consumer and the producer.

You can specify different values by setting additional env variables, e.g.:

    export OAUTH_CLIENT_ID=kafka-client
    export OAUTH_CLIENT_SECRET=kafka-client-secret

If you already have the `access_token` by having obtained it manually, you can use it directly by setting the following env variable:

    export OAUTH_ACCESS_TOKEN=<your-access-token>

Or, if you already have the `refresh_token` that you have obtained manually, you can use that by serring the following env variable:

    export OAUTH_REFRESH_TOKEN=<your-refresh-token>



You can now use an IDE to run the example Kafka Consumer, or you can run from shell:

    cd examples/consumer
    java -cp 'target/*:target/lib/*' io.strimzi.examples.consumer.ExampleConsumer

Similarly, you can run the example Kafka Producer:

    cd examples/producer
    java -cp 'target/*:target/lib/*' io.strimzi.examples.producer.ExampleProducer

or the concurrent producer: 

    export OAUTH_CLIENT_ID=kafka-producer-client
    export OAUTH_CLIENT_SECRET=kafka-producer-client-secret

    java -cp 'target/*:target/lib/*' io.strimzi.examples.producer.ExampleConcurrentProducer


Troubleshooting
---------------

If `kafka` fails to start with 'Invalid cluster.id in: /tmp/kraft-combined-logs/meta.properties.' error, the reason may be that an existing container named `kafka` is being reused when running `docker-compose`.
You can try to fix that by removing the existing `kafka` container:

    docker rm -f kafka

Other reasons are possible as well, especially if you have changed the configuration of the Kafka broker, or the certificates.

If you see exception messages like any of the following:
* `org.apache.kafka.common.KafkaException: Failed to construct kafka consumer`
* `org.apache.kafka.common.KafkaException: Failed to create new NetworkClient`
* `org.apache.kafka.common.KafkaException: javax.security.auth.login.LoginException: An internal error occurred while retrieving token from callback handler`

they are most likely the result of failing to connect to the Keycloak server.

You can confirm this by the presence of other exception messages:

* `java.net.ConnectException: Connection refused`
* `java.io.IOException: Failed to connect to: https://keycloak:8443/realms/demo/protocol/openid-connect/token`

Most likely your `keycloak` (or `hydra`) container isn't running, or you may not have modified your `/etc/hosts` properly as described in [Preparing](#preparing).
