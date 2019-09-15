Example Kafka Consumer
======================

This projects demonstrates a Kafka client that uses OAuth2 to establish Kafka session.


Building
--------
    mvn clean install


Preparing
---------

Determine your machine's local network IP address, and add keycloak entry to your `/etc/host`.

You can use `ifconfig` utility. On macOS for example you can run:

    ifconfig en0 | grep 'inet ' | awk '{print $2}'

Then, add keycloak entry to your `/etc/hosts` file 

    <YOUR_IP_ADDRESS>    keycloak


Running without SSL
-------------------

You can use an IDE to run example clients, or you can run from shell:

    java -cp consumer/target/*:consumer/target/lib/* io.strimzi.examples.consumer.ExampleConsumer


Running with SSL
----------------

You need to set additional env variables in order to configure truststore, and turn off certificate hostname validation:

    OAUTH_SSL_TRUSTSTORE_LOCATION=../docker/keycloak-import/config/keycloak.client.truststore.p12
    OAUTH_SSL_TRUSTSTORE_PASSWORD=changeit
    OAUTH_SSL_TRUSTSTORE_TYPE=pkcs12
    OAUTH_TOKEN_ENDPOINT_URI=https://keycloak:8443/auth/realms/demo/protocol/openid-connect/token

    # If certificate hostname didn't match 'keycloak' you could use the following line to skip hostname verification
    #OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM=

You can now use an IDE to run example clients, or you can run from shell:

    java -cp consumer/target/*:consumer/target/lib/* io.strimzi.examples.consumer.ExampleConsumer
    
By default, producer authenticates with client credentials using client id, and client secret.

See [examples README](../README.md) for other authentication options.
