[![Build Status](https://travis-ci.org/strimzi/strimzi-kafka-oauth.svg?branch=master)](https://travis-ci.org/strimzi/strimzi-kafka-oauth)
[![GitHub release](https://img.shields.io/github/release/strimzi/strimzi-kafka-oauth.svg)](https://github.com/strimzi/strimzi-kafka-oauth/releases/latest)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.strimzi/oauth/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.strimzi/oauth)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio.svg?style=social&label=Follow&style=for-the-badge)](https://twitter.com/strimziio)

Strimzi Kafka OAuth
===================

Strimzi Kafka OAuth modules provide support for OAuth2 as authentication mechanism when establishing a session with Kafka broker.


OAuth2 for Authentication
-------------------------

One of the advantages of OAuth2 compared to direct client-server authentication is that client credentials are never shared with the server.
Rather, there exists a separate OAuth2 authorization server that application clients and application servers communicate with.
This way user management, authentication, and authorization is 'outsourced' to a third party - authorization server.  

User authentication is then an outside step which user manually performs to obtain an access token or a refresh token, which grants a limited set of permissions to a specific client app.

In the simplest case, the client application can authenticate in its own name using client 
credentials - client id and secret.
While the client secret is in this case packaged with application client, the benefit is still that it is not shared with application server (Kafka broker in our case) - the client first performs authentication against OAuth2 authorization server in exchange for an access token, which it then sends to the application server instead of its secret.
Access tokens can be independently tracked and revoked at will, and represent a limited access to resources on application server.

In a more advanced case, user authenticates and authorizes the application client in exchange for a token.
The client application is then packaged with only a long lived access token or a long lived refresh token.
User's username and password are never packaged with application client.
If access token is configured it is sent directly to Kafka broker during session initialisation.
But if refresh token is configured, it is first used to ask authorization server for a new access token, which is then sent to Kafka broker to start a new authenticated session.

When using refresh tokens for authentication the retrieved access tokens can be relatively short-lived which puts a time limit on potential abuse of a leaked access token.
Repeated exchanges with authorization server also provide centralised tracking of authentication attempts which is something that can be desired.

A developer authorizing the client application with access to Kafka resources will access authorization server directly, using a web based or CLI based tool to sign in and generate access token or refresh token for application client.

As hinted above, OAuth2 allows for different implementations, multiple layers of security, and it is up to the developer, taking existing security policies into account, on how exactly they would implement it in their applications.


OAuth2 for Authorization
------------------------

Authentication is the procedure of establishing if the user is who they claim they are.
Authorization is the procedure of deciding if the user is allowed to perform some action using some resource.
Kafka brokers by default allow all users full access - there is no specific authorization policy in place.
Kafka comes with an implementation of ACL based authorization mechanism where access rules are saved in ZooKeeper and replicated across brokers. 

Authorization in Kafka is implemented completely separately and independently of authentication.
Thus, it is possible to configure Kafka brokers to use OAuth2 based authentication, and at the same time the default ACL authorization. 

At this time Strimzi Kafka OAuth doesn't provide authorization that would integrate with JWT token claims or UMA authorization services.


Kafka OAuth2 Support
--------------------

Kafka comes with basic OAuth2 support in the form of SASL based authentication module which provides client-server retrieval, exchange and validation of access token used as credentials.
For real world usage, extensions have to be provided in the form of JAAS callback handlers which is what Strimzi Kafka OAuth does.


Building
--------

    mvn clean install


Installing
----------

Copy the following jars into your Kafka libs directory:

    oauth-common/target/kafka-oauth-common-*.jar
    oauth-server/target/kafka-oauth-server-*.jar
    oauth-keycloak-authorizer/target/kafka-oauth-keycloak-authorizer-*.jar
    oauth-client/target/kafka-oauth-client-*.jar
    oauth-client/target/lib/keycloak-common-*.jar
    oauth-client/target/lib/keycloak-core-*.jar
    oauth-client/target/lib/bcprov-*.jar


Configuring the Kafka Broker as a Server
----------------------------------------

In Kafka server.properties enable specify the following:

    # Enable OAUTHBEARER as SASL mechanism
    sasl.enabled.mechanisms=OAUTHBEARER

    # Configure a listener for client applications
    # Replace 'kafka' with a valid hostname, resolvable by clients
    listeners=CLIENTS://kafka:9092

    # Specify the protocol for the listener
    listener.security.protocol.map=CLIENTS:SASL_PLAINTEXT

    # Enable Strimzi Kafka OAuth
    listener.name.client.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
    listener.name.client.oauthbearer.sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
    listener.name.client.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler


Configure Strimzi Kafka OAuth through environment variables or system properties.
You can convert environment variables to system properties by converting all letters to lowercase, and all underscores to dots.  

    # Specify OAuth2 Token Endpoint URL to your authorization server
    # For production you should always use https url - see examples/docker/kafka-oauth-strimzi/compose-ssl.yml
    export OAUTH_TOKEN_ENDPOINT_URI=https://auth-server/token

    # Specify valid Issuer URI
    # Only access tokens issued by this issuer will be accepted
    export OAUTH_VALID_ISSUER_URI=https://auth-server
    
    # Specify configured client id of Kafka broker - same for all brokers.
    # You have to manually register this client with your authorization server
    # (you may use a different client id and secret accordingly).
    export OAUTH_CLIENT_ID=kafka-broker

    # Specify configured secret for Kafka broker - same for all brokers 
    export OAUTH_CLIENT_SECRET=kafka-broker-secret

Additionally, if you've obtained a long lived refresh token for Kafka brokers, you can specify that:

    export OAUTH_REFRESH_TOKEN=<YOUR_REFRESH_TOKEN_FOR_KAFKA_BROKERS>

Alternatively, if you've created a long lived access token for Kafka brokers, you can just specify the access token:

    export OAUTH_ACCESS_TOKEN=<YOUR_ACCESS_TOKEN_FOR_KAFKA_BROKERS>

If your authorization server issues JWT access tokens, and provides a JWKS certificates endpoint, you can configure fast local token validation:

    # Specify JWKS Endpoint URL
    export OAUTH_JWKS_ENDPOINT_URI=https://auth-server/jwks

    # Period between consecutive certificate endpoint refreshes
    # (Optional, default value is 300)
    export OAUTH_JWKS_REFRESH_SECONDS=300

    # If you are willing to trust potentially revoked certificates
    # during connectivity issues to authorization server
    # specify a bigger value of certificate expiry time
    # (Optional, default value is 360)
    export OAUTH_JWKS_EXPIRY_SECONDS=360

If, on the other hand, you're using non-JWT (opaque) access tokens, then you have to configure an introspection endpoint and token validation will be delegated to your authorization server.

    # Specify OAuth2 Introspection Endpoint URL
    export OAUTH_INTROSPECTION_ENDPOINT_URI=https://auth-server/introspection

You may need to provide a custom truststore for connecting to your authorization server or may need to turn off hostname certificate check.
Use the following configuration:

      # Truststore config for connecting to secured authorization server
      OAUTH_SSL_TRUSTSTORE_LOCATION: /path/to/truststore.p12
      OAUTH_SSL_TRUSTSTORE_PASSWORD: changeit
      OAUTH_SSL_TRUSTSTORE_TYPE: pkcs12
      
      #OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION=

      # Turn off certificate host name check
      OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM: "" 


Configuring the Kafka Broker as a Client
----------------------------------------

You can also use OAuth2 for Broker to Broker authentication.

In Kafka `server.properties` specify:

    # Specify the interbroker listener - this is usually not the same as for external clients, but in this example it is
    inter.broker.listener.name: CLIENTS

    # Specify OAUTHBEARER to be used as interbroker SASL protocol
    sasl.mechanism.inter.broker.protocol: OAUTHBEARER


If you have turned on ACL authorization in Kafka brokers, then you need to properly set an admin user. 
By default, access token's 'sub' claim is used as user id. You may want to use another claim provided in access token as an alternative user id (username, email ...). 
It depends on your authorization server and its configuration, what is available.

In Kafka `server.properties` specify:

    # Add user id present as JWT token's 'sub', or alternative claim, in your kafka-broker client's access token
    admin.users=User:service-account-kafka-broker

Configure alternative user id extraction through Strimzi Kafka OAuth environment variable or system property.

    export OAUTH_USERNAME_CLAIM=preferred_username

Also provide Kafka broker credentials in the same way as with any Kafka client as described in the next chapter.


Configuring the Kafka Client
----------------------------

Client applications like producers, consumers, connectors have to be configured through client properties.

For example, for `org.apache.kafka.clients.producer.KafkaProducer` or `org.apache.kafka.clients.consumer.KafkaConsumer`, set the following properties:

    sasl.mechanism=OAUTHBEARER
    sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
    sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler

To further configure OAuth2 authentication, use environment variables or system properties.
You can convert environment variables to system properties by converting all letters to lowercase, and all underscores to dots.  

    # Specify OAuth2 Token Endpoint URL to your authorization server
    # For production you should always use https url - see examples/docker/kafka-oauth-strimzi/compose-ssl.yml
    export OAUTH_TOKEN_ENDPOINT_URI=https://auth-server/token

    # Specify configured client id of client app
    # You have to manually register this client with your authorization server
    export OAUTH_CLIENT_ID=kafka-producer-client

    # Specify any configured secret for your client
    # (or not if your client is considered public, and has no secret)
    export OAUTH_CLIENT_SECRET=kafka-producer-client-secret

Additionally, if you've obtained a long lived refresh token for your client, you can specify that:

    export OAUTH_REFRESH_TOKEN=<YOUR_REFRESH_TOKEN_FOR_KAFKA_PRODUCER_CLIENT>

Alternatively, if you've created a long lived access token for your client, you can just specify the access token:

    export OAUTH_ACCESS_TOKEN=<YOUR_ACCESS_TOKEN_FOR_KAFKA_PRODUCER_CLIENT>

If you're using alternative username claim on the Kafka brokers, and want to see proper principal during debugging on the client, also configure:

    export OAUTH_USERNAME_CLAIM=preferred_username


Configuring the authorization server
------------------------------------

At your authorization server, you need to configure a client for Kafka broker, and a client for each of your client applications.

Also, you may want each developer to have a user account in order to configure user based access controls.

Configuring users, clients, and authorizing clients, obtaining access tokens, and refresh tokens are steps that are specific to the authorization server that you use. Consult your authorization server's documentation.


Building
--------

    mvn clean install


Demo
----

See [examples README](examples/README.md).
