Example Kafka Producer
======================

This projects demonstrates a Kafka client that uses OAuth2 to establish Kafka session.


Building
--------

    mvn clean install


Preparing
---------

Make sure that the following ports on your host machine are free: 9092, 2181 (Kafka), 8080, 8443 (Keycloak), 4444, 4445 (Hydra).

Then, you have to add some entries to your `/etc/hosts` file:

    127.0.0.1            keycloak
    127.0.0.1            hydra
    127.0.0.1            kafka

That's needed for host resolution, because Kafka brokers and Kafka clients connecting to Keycloak / Hydra have to use the 
same hostname to ensure compatibility of generated access tokens.

Also, when Kafka client connects to Kafka broker running inside docker image, the broker will redirect the client to: kafka:9292.
    

Running without SSL
-------------------

You can use an IDE to run example clients, or you can run from shell:

    java -cp target/*:target/lib/* io.strimzi.examples.producer.ExampleProducer


Running with SSL
----------------

You need to set additional env variables in order to configure truststore, and turn off certificate hostname validation:

    export OAUTH_SSL_TRUSTSTORE_LOCATION=../docker/certificates/ca-truststore.p12
    export OAUTH_SSL_TRUSTSTORE_PASSWORD=changeit
    export OAUTH_SSL_TRUSTSTORE_TYPE=pkcs12
    
To use Keycloak as authorization server set url of Keycloak's demo realm token endpoint:

    export OAUTH_TOKEN_ENDPOINT_URI=https://keycloak:8443/auth/realms/demo/protocol/openid-connect/token

To use Hydra as authorization server set url of Hydra's token endpoint:

    export OAUTH_TOKEN_ENDPOINT_URI=https://hydra:4444/oauth2/token

If using Hydra with opaque tokens, also set:

    export OAUTH_TOKENS_NOT_JWT=true

If using Hydra with JWT tokens, then set:

    export OAUTH_USERNAME_CLAIM=sub
    
You can now use an IDE to run example clients, or you can run from shell:

    java -cp target/*:target/lib/* io.strimzi.examples.producer.ExampleProducer
    
By default, producer authenticates with client credentials using client id, and client secret.

See [examples README](../README.md) for other authentication options.
