Spring Authorization Server
===========================

This project builds and runs Spring Authorization Server as a docker container.


Building
--------

    mvn clean install


Running
-------

    docker run --rm -ti --name spring strimzi/example-spring


Using
-----

Configure your kafka-broker with the following settings:

    oauth.introspection.endpoint.uri=http://localhost:8080/oauth/check-token
    oauth.client.id=kafka
    oauth.client.secret=kafkasecret
    oauth.check.issuer=false
    oauth.check.access.token.type=false
    oauth.username.claim=user_name
    oauth.failover.username.claim=client_id
    oauth.failover.username.prefix=client-account-


Configure your Kafka client, by obtaining the token as user

    