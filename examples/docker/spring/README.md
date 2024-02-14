Spring Authorization Server
===========================

This project builds and runs Spring Authorization Server as a docker container.
The accompanying Kafka broker example is configured to use this authorization server for Kafka authentication (not for Kafka authorization).
Client definitions are defined in code in [SecurityConfig.java](file://./src/main/java/io/strimzi/examples/spring/SecurityConfig.java) file.


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

Spring Authorization Server exposes multiple OAuth2 / OIDC endpoints. For our purposes the important ones are:

* The token endpoint: http://spring:8080/oauth2/token
* The signing keys endpoint: http://spring:8080/oauth2/jwks
* The introspection endpoint: http://spring:8080/oauth2/introspect

This example demonstrates using `client_credentials` grant with two different clients. First client `kafkaclient` produces 
JWT access tokens which can be validated by using the JWKS endpoint. The second client `kafkaclient2` produces opaque access tokens
which can only be validated by using the Introspection endpoint.

For example, in order to use the JWKS endpoint you could configure your Kafka broker listener with the following JAAS config parameters:

    oauth.jwks.endpoint.uri=http://spring:8080/oauth2/jwks
    oauth.jwks.ignore.key.use=true    
    oauth.valid.issuer.uri=http://spring:8080
    oauth.check.access.token.type=false

In order to use the Introspection endpoint you could configure your Kafka broker listener with the following JAAS config parameters:

    oauth.introspect.endpoint.uri=http://spring:8080/oauth2/introspect
    oauth.valid.issuer.uri=http://spring:8080
    oauth.client.id=kafkabroker
    oauth.client.secret=kafkabrokersecret
    oauth.access.token.is.jwt=false
    oauth.check.access.token.type=false


### Token validation using the Introspection endpoint (JWT and opaque tokens)

You can run the prepared Kafka example from parent directory (`examples/docker`) - make sure the Spring Authorization Server is up before running this command:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring.yml up --build


You can authenticate as a Kafka client using a service account `kafkaclient` to obtain the access token:

    curl http://spring:8080/oauth2/token -d "grant_type=client_credentials&scope=profile" -u kafkaclient:kafkaclientsecret

Or if you want to automate:

    RESPONSE=$(curl -s http://spring:8080/oauth2/token -d "grant_type=client_credentials&scope=profile" -u kafkaclient:kafkaclientsecret)
    ACCESS_TOKEN=$(echo "$RESPONSE" | sed -E -e "s#.*\"access_token\":\"([^\"]+)\",\".*#\1#")

You can take the role of the Kafka broker to check if the token is valid:

    curl http://spring:8080/oauth2/introspect -d "token=$ACCESS_TOKEN"  -u kafkabroker:kafkabrokersecret

In this case the returned access token was a JWT token which you can confirm by using the following helper script (from `examples/docker` directory):

    ./kafka-oauth-strimzi/kafka/jwt.sh $ACCESS_TOKEN 


If you authenticate as client `kafkaclient2` the returned access token will be an opaque token (not JWT):

    curl http://spring:8080/oauth2/token -d "grant_type=client_credentials&scope=profile" -u kafkaclient2:kafkaclient2secret

Or if you want to automate:

    RESPONSE2=$(curl -s http://spring:8080/oauth2/token -d "grant_type=client_credentials&scope=profile" -u kafkaclient2:kafkaclient2secret)
    ACCESS_TOKEN2=$(echo "$RESPONSE2" | sed -E -e "s#.*\"access_token\":\"([^\"]+)\",\".*#\1#")

You can again take the role of the Kafka broker to check if the token is valid:

    curl http://spring:8080/oauth2/introspect -d "token=$ACCESS_TOKEN2"  -u kafkabroker:kafkabrokersecret

You can not introspect the opaque token as it's just a random string without any internal structure that could be parsed.
Using the introspection endpoint is the only way to validate such a token.


### Connecting with a Kafka client

You can try both JWT access token and the opaque access token with a Kafka client that connects to the example Kafka broker.

Start a new kafka container for the client:

    docker run -ti --name kafka-client --network docker_default strimzi/example-kafka /bin/sh

```
$ echo 'security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
oauth.client.id="kafkaclient" \
oauth.client.secret="kafkaclientsecret" \
oauth.token.endpoint.uri="http://spring:8080/oauth2/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler' > $HOME/client.properties
```

Check which topics exist:

    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/client.properties --list


Run a console kafka producer:

    bin/kafka-console-producer.sh --broker-list kafka:9092 --topic a_messages   --producer.config=$HOME/client.properties
    # type some messages then press CTRL-C or CTRL-D

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic a_messages --from-beginning --consumer.config $HOME/client.properties --group a_consumer_group_1
    # Press CTRL-C to exit

You can also use `kafkaclient2` to see how it works with an opaque token.


### Token validation using JWT key signature checking (JWT tokens only, opaque tokens not supported)

There is a Kafka broker configuration example where the listener is configured to use the JWKS endpoint. Such a setup can only be used with JWT tokens.

You can run the prepared Kafka example from parent directory (`examples/docker`) - make sure the Spring Authorization Server is up before running this command:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose-spring-jwt.yml up --build

You can run the Kafka client using the same commands as in the previous chapter.

If you try to authenticate the client as `kafkaclient2` the authentication will fail. While the Kafka client will successfully obtain the token, 
the validation of the token will fail on Kafka broker due to not being a JWT token. 