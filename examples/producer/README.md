Example Kafka Producer
======================

This projects demonstrates a Kafka client that uses OAuth to establish Kafka session.


### Building

    mvn clean install


### Preparing

Determine your machine's local network IP address, and set it as env variable.

    export KEYCLOAK_IP=<YOUR_IP_ADDRESS>

For example, on macOS:

    export KEYCLOAK_IP=$(ifconfig en0 | grep 'inet ' | awk '{print $2}')


### Running without SSL

You can use an IDE to run example clients, or you can run from shell:

    java -cp producer/target/*:producer/target/lib/* io.strimzi.examples.producer.ExampleProducer


### Running with SSL

You need to set additional env variables in order to configure truststore, and turn off certificate hostname validation:

    OAUTH_SSL_TRUSTSTORE_LOCATION=../docker/keycloak-import/config/keycloak.client.truststore.p12
    OAUTH_SSL_TRUSTSTORE_PASSWORD=changeit
    OAUTH_SSL_TRUSTSTORE_TYPE=pkcs12
    OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM=
    OAUTH_TOKEN_ENDPOINT_URI=https://${KEYCLOAK_IP}:8443/auth/realms/demo/protocol/openid-connect/token



You can now use an IDE to run example clients, or you can run from shell:

    java -cp producer/target/*:producer/target/lib/* io.strimzi.examples.producer.ExampleProducer
    
By default, producer authenticates with client credentials using client id, and client secret.

See [examples README](../README.md) for other authentication options.
