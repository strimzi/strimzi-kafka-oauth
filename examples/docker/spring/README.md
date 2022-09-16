Spring Authorization Server
===========================

This project builds and runs Spring Authorization Server as a docker container.


Building
--------

    mvn clean install


Running
-------

You can run it by using docker-compose in which case run it from parent directory (`examples/docker`):

    docker-compose -f compose.yml -f spring/compose.yml up

Or by using `docker` (explicitly setting the network makes `spring` hostname visible from other containers):

    docker run --rm -ti -p 8080:8080 --network docker_default --name spring strimzi/example-spring


Using
-----

Make sure to add `spring` entry to your `/etc/hosts` as explained [here](../README.md#preparing).

Configure your Kafka broker with the following settings:

    oauth.introspection.endpoint.uri=http://spring:8080/oauth/check_token
    oauth.token.endpoint.uri=http://spring:8080/oauth/token
    oauth.client.id=kafka
    oauth.client.secret=kafkasecret
    oauth.scope=any
    oauth.access.token.is.jwt=false
    oauth.check.issuer=false
    oauth.username.claim=user_name
    oauth.fallback.username.claim=client_id
    oauth.fallback.username.prefix=client-account-


Spring authorization server by default uses opaque tokens (non-JWT) which means validation has to use the introspection endpoint.
The example single-broker cluster uses OAuth2 for inter-broker communication as well as Kafka client communication which requires token endpoint to be configured.
Authorization server's Token endpoint requires `scope` to be specified.
The Introspection endpoint returns no information about issuer, so we have to disable that check.
User information is sent depending on the type of authentication. 
If Kafka client sends a token obtained by a user using `password` grant, then `user_name` attribute 
of introspection endpoint response contains user's username. If Kafka client sends a token obtained in as the client by using `client_credentials` grant, then no `user_name` is set, but `client_id` is.
By using username prefix we can quickly differentiate client accounts from user accounts - client_id with the same name as another username will become a different principal.
We can then use Kafka Simple ACL Authorization to define ACL policies based on authenticated principal.


You can run the prepared example from parent directory (`examples/docker`):

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring.yml up
    

Check the logging output of the Spring container for the default user's password:

    docker logs spring | grep "Using generated security password:"

Set the password as env var `PASSWORD`:

    export PASSWORD=$(docker logs spring | grep "Using generated security password:" | awk '{print $NF}')


Configure your Kafka client by obtaining the token as user `user`:

    curl spring:8080/oauth/token -d "grant_type=password&scope=read&username=user&password=$PASSWORD" -u kafka:kafkasecret


Use the following configuration options to configure your client with the refresh token:

    oauth.token.endpoint.uri=http://spring:8080/oauth/token
    oauth.refresh.token=$REFRESH_TOKEN
    oauth.scope=any
    oauth.access.token.is.jwt=false

