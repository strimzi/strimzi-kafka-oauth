[![Build Status](https://travis-ci.org/strimzi/strimzi-kafka-oauth.svg?branch=master)](https://travis-ci.org/strimzi/strimzi-kafka-oauth)
[![GitHub release](https://img.shields.io/github/release/strimzi/strimzi-kafka-oauth.svg)](https://github.com/strimzi/strimzi-kafka-oauth/releases/latest)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.strimzi/oauth/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.strimzi/oauth)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio.svg?style=social&label=Follow&style=for-the-badge)](https://twitter.com/strimziio)

Strimzi Kafka OAuth
===================

Kafka comes with basic OAuth2 support in the form of SASL based authentication module which provides client-server retrieval, exchange and validation of access token used as credentials.
For real world usage, extensions have to be provided in the form of JAAS callback handlers which is what Strimzi Kafka OAuth does.

Strimzi Kafka OAuth modules provide support for OAuth2 as authentication mechanism when establishing a session with Kafka broker.

<!-- TOC depthFrom:2 -->

- [OAuth2 for Authentication](#oauth2-for-authentication)
- [OAuth2 for Authorization](#oauth2-for-authorization)
- [Building](#building)
- [Installing](#installing)
- [Configuring the authorization server](#configuring-the-authorization-server)
- [Configuring the Kafka Broker](#configuring-the-kafka-broker)
  - [Configuring the Kafka Broker authentication](#configuring-the-kafka-broker-authentication)
    - [Configuring the listeners](#configuring-the-listeners)
    - [Configuring the JAAS login module](#configuring-the-jaas-login-module)
    - [Enabling the custom callbacks](#enabling-the-custom-callbacks)
    - [Configuring the OAuth2](#configuring-the-oauth2)
      - [Configuring the token validation](#configuring-the-token-validation)
        - [Validation using the JWKS endpoint](#validation-using-the-jwks-endpoint)
        - [Validation using the introspection endpoint](#validation-using-the-introspection-endpoint)
      - [Configuring the client side of inter-broker communication](#configuring-the-client-side-of-inter-broker-communication)
  - [Configuring the Kafka Broker authorization](#configuring-the-kafka-broker-authorization)
    - [Enabling the KeycloakRBACAuthorizer](#enabling-the-keycloakrbacauthorizer)
    - [Configuring the KeycloakRBACAuthorizer](#configuring-the-keycloakrbacauthorizer)
- [Configuring the Kafka Client](#configuring-the-kafka-client)
  - [Enabling SASL_OAUTHBEARER mechanism](#enabling-sasl-oauthbearer-mechanism)
  - [Configuring the JAAS login module](#configuring-the-jaas-login-module-client)
  - [Enabling the custom callbacks](#enabling-the-custom-callbacks-client)
  - [Configuring the OAuth2](#configuring-the-oauth2-client)
  - [Client config example](#client-config-example)
- [Configuring the TLS truststore](#configuring-the-tls-truststore)
- [Demo](#demo)
  
<!-- /TOC -->

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

Since version 0.3.0 Strimzi Kafka OAuth provides token-based authorization using Keycloak as authorization server, and taking advantage of [Keycloak Authorization Services](https://www.keycloak.org/docs/latest/authorization_services/).
See [examples authorization README](examples/README-authz.md) for a demonstration on how to install, and use [KeycloakRBACAuthorizer](oauth-keycloak-authorizer/src/main/java/io/strimzi/kafka/oauth/server/authorizer/KeycloakRBACAuthorizer.java) which implements this functionality.

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

Configuring the authorization server
------------------------------------

At your authorization server, you need to configure a client for Kafka broker, and a client for each of your client applications.

Also, you may want each developer to have a user account in order to configure user based access controls.

Configuring users, clients, and authorizing clients, obtaining access tokens, and refresh tokens are steps that are specific to the authorization server that you use.
Consult your authorization server's documentation.

If you use KeycloakRBACAuthorizer for authorization then you also use your authorization server (Keycloak) to use Authorization Services functionality to configure cevurity policies and permissions for users and service accounts. 
 
Configuring the Kafka Broker 
----------------------------

Kafka uses JAAS to bootstrap custom authentication mechanisms. 
Strimzi Kafka OAuth therefore needs to use JAAS configuration to activate SASL_OAUTHBEARER, and install its own implementations of the callback classes.
This is true for configuring the server side of the Kafka Broker, as well as for the Kafka client side - when using OAuth 2 for inter-broker communication.

The authentication configuration specific to the Strimzi Kafka OAuth can be specified as part of JAAS configuration in the form of JAAS parameter values. 
The authorization configuration for KeycloakRBACAuthorizer is specified as `server.properties` key-value pairs.
Both authentication and authorization configuration specific to Strimzi Kafka OAuth can also be set as ENV vars, or as Java system properties.
The limitation here is that authentication configuration specified in this manner can not be listener-scoped. 

### Configuring the Kafka Broker authentication

Note: Strimzi Kafka OAuth can not be used for Kafka Broker to Zookeeper authentication. It only supports Kafka Client to Kafka Broker authentication (including inter-broker communication).

There are several steps to configuring the Kafka Broker:

#### Configuring the listeners

In order to configure Strimzi Kafka OAuth for the listener, you first need to enable SASL security for the listener, and enable SASL_OAUTHBEARER mechanism.

For example, let's have two listeners, one for inter-broker communication (REPLICATION), and one for Kafka clients to connect (CLIENT):

```
listeners=REPLICATION://kafka:9991,CLIENT://kafka:9092
listener.security.protocol.map=REPLICATION:PLAINTEXT,CLIENT:SASL_PLAINTEXT
```

Here we have configured the `REPLICATION` listener to not use any authentication, and we have configured the `CLIENT` listener to use SASL authentication over insecure connection. 
Note that these are examples, for production you should almost certainly not use PLAINTEXT, or SASL_PLAINTEXT.

Having these two listeners, the one called `CLIENT` fulfils the precondition for Strimzi Kafka OAuth in that it is configured to use SASL based authentication.

The next thing to do is to enable SASL_OAUTHBEARER mechanism:

    sasl.enabled.mechanisms=OAUTHBEARER

#### Configuring the JAAS login module

In JAAS configuration we do two things:
* we activate a specific JAAS login module - for Strimzi Kafka OAuth that is always the `org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule` class which implements Kafka's SASL_OAUTHBEARER authentication mechanism.
* we specify configurations used by the login module, and by our custom extensions - the server callback handler and / or the login callback handler (which we will install in [the next step](#enabling-the-custom-callbacks)).

JAAS configuration can be specified inside `server.properties` file using the listener-scoped `sasl.jaas.config` key. 
Assuming there is the `CLIENT` listener configured as shown above, we can specify configuration specifically for that listener:

```
listener.name.client.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
    oauth.jwks.endpoint.uri="https://server/keys" ;
```

Note the `listener.name.client.oauthbearer.` prefix for the key. The word `client` in the key refers to the `CLIENT` listener. 
In this case we are configuring the validator with fast local signature check that uses the JWKS endpoint provided by authorization server.

Here we specified the `oauth.jwks.endpoint.uri` configuration key and its value as a JAAS configuration parameter. 
Any Strimzi Kafka OAuth keys that begin with `oauth.` can be specified this way - scoped to the individual listener.

A value of the scoped `sasl.jaas.config` always starts with:
`org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required `

Followed by zero or more configuration parameters, separated by a whitespace, and ended with a semicolon - `;`
Inside `server.properties` this has to be a single line, or if multi-line, each non-final line has to be ended with a backslash `\`.

#### Enabling the custom callbacks

The custom callbacks are enabled per listener in `server.properties` using the listener-scoped configuration:
On the Kafka Broker we need to install two callback handlers - the validator callback handler, and the login callback handler:
```
listener.name.client.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler
listener.name.client.oauthbearer.sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
```

While the second one is only used if you configure inter-broker communication, it needs to be installed in order to prevent the default behaviour by `OAuthBearerLoginModule` which would require essentially unnecessary additional JAAS config parameters. 

#### Configuring the OAuth2

Strimzi Kafka OAuth library uses properties that start with `oauth.*` to configure authentication, and properties that start with `strimzi.*` to configure authorization.

Authentication properties are best specified as JAAS config parameters as part of the value of listener-scoped `sasl.jaas.config` property in `server.properties`.
See [Configuring the JAAS login module](#configuring-the-jaas-login-module).
At this time the authentication properties (`oauth.*`) can't be specified as `server.properties` property keys - they will not have any effect.

They _can_ be specified as ENV variables, or as Java system properties in which case they override any JAAS config parameter values.
For environment variables the `oauth.*` and `strimzi.*` property keys can be uppercased with `.` replaced by `_`. 
For example, to specify `oauth.client.id` as the ENV var, you can use `OAUTH_CLIENT_ID`.

In terms of precedence the order of looking for a configuration is the following:
- System property `oauth.client.id`
- ENV var `OAUTH_CLIENT_ID`
- ENV var `oauth.client.id`
- JAAS config property `oauth.client.id`

Whichever is found first is the one that is used.

Similarly for authorization the `strimzi.*` properties follow a similar lookup mechanism except that they don't use JAAS config, but are specified as `server.properties` keys.
For example, the order of looking for configuration for `strimzi.client.id` would be the following:
- System property `strimzi.client.id`
- ENV var `STRIMZI_CLIENT_ID`
- ENV var `strimzi.client.id`
- `server.properties` property `strimzi.client.id`

##### Configuring the token validation

The most essential OAuth2 configuration on the Kafka Broker is the configuration related to validation of the access tokens passed from Kafka clients to the Kafka broker during SASL based authentication mechanism.

There are two options for token validation:
- Using the JWKS endpoint in combination with signed JWT formatted access tokens
- Using the introspection endpoint

###### Validation using the JWKS endpoint

If your authorization server generates JWT tokens, and exposes the JWKS Endpoint then using JWKS endpoint is most efficient,
since it does not require contacting the authorization server whenever a new Kafka client connects to the Kafka Broker.

Specify the following `oauth.*` properties:
- `oauth.jwks.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/certs")
- `oauth.valid.issuer.uri` (e.g.: "https://localhost:8443/auth/realms/demo" - only access tokens issued by this issuer will be accepted)

JWT tokens contain unique user identification in `sub` claim. However, this is often a long number or a UUID, but we usually prefer to use human readable usernames, which may also be present in JWT token.
Use `oauth.username.claim` to map the claim (attribute) where the username is stored:
- `oauth.username.claim` (e.g.: "preferred_username")

If your authorization server uses ECDSA encryption then you need to enable the BouncyCastle JCE crypto provider:
- `oauth.crypto.provider.bouncycastle` (e.g.: "true")

Depending on your authorization server you may need to relax some checks: 
- `oauth.check.access.token.type` (e.g.: "false" - do not require `"typ": "Bearer"` in JWT token)

You can control how often the keys used for signature checks are refreshed and when they expire:
- `oauth.jwks.refresh.seconds` (e.g.: "300" - that's the default value - keys are refreshed every 5 minutes)
- `oauth.jwks.expiry.seconds` (e.g.: "360" - that's the default value - keys expire 6 minutes after they are loaded)

All access tokens can be invalidated by rotating the keys on authorization server and expiring old keys.

###### Validation using the introspection endpoint

When your authorization server is configured to use opaque tokens (not JWT) or if it does not expose JWKS endpoint, you have no other option but to use the introspection endpoint.
This will result in Kafka Broker making a request to authorization server every time a new Kafka client connection is established.

Specify the following `oauth.*` properties:
- `oauth.introspection.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/token/introspect")
- `oauth.valid.issuer.uri` (e.g.: "https://localhost:8443/auth/realms/demo" - only access tokens issued by this issuer will be accepted)
- `oauth.client.id` (e.g.: "kafka" - this is the client configuration id for Kafka Broker)
- `oauth.client.secret` (e.g.: "kafka-secret")

Introspection endpoint should be protected. The `oauth.client.id` and `oauth.client.secret` specify Kafka Broker credentials for authenticating to access the introspection endpoint. 

If you have a DEBUG logging configured for the `io.strimzi` category you may need to specify the following to prevent warnings about access token not being JWT:
- `oauth.access.token.is.jwt` (e.g.: "false")

##### Configuring the client side of inter-broker communication

All the Kafka Brokers in the cluster should be configured with the same clientId and clientSecret, and the corresponding user should be added to `super.users` since inter-broker client requires super-user permissions.

Specify the following `oauth.*` properties:
- `oauth.token.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/token")
- `oauth.client.id` (e.g.: "kafka" - this is the client configuration id for Kafka Broker)
- `oauth.client.secret` (e.g.: "kafka-secret")
- `oauth.username.claim` (e.g.: "preferred_username")

Also specify the username corresponding to client account identified by `oauth.client.id` in `super.users` property in `server.properties` file:
- `super.users` (e.g.: "User:service-account-kafka") 

This is not a full set of available `oauth.*` properties. All the `oauth.*` properties described in the next chapter about configuring the Kafka clients also apply to configuring the client side of inter-broker communication. 

### Configuring the Kafka Broker authorization

Strimzi Kafka OAuth provides support to centrally manage not only users and clients, but also permissions to Kafka broker resources - topics, consumer groups, configurations ...

Support for this works specifically with Keycloak Authorization Services.

By default, authorization is not enabled on Kafka Broker. There is `kafka.security.auth.SimpleAclAuthorizer` that comes with Kafka out-of-the-box, and is well documented in [Kafka Documentation](https://kafka.apache.org/documentation/). 

Strimzi Kafka OAuth provides an alternative authorizer - `io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer`.
`KeycloakRBACAuthorizer` uses the access token and the Token Endpoint of the same Keycloak realm used for OAuth2 authentication as a source of permission grants for the authenticated session.

#### Enabling the KeycloakRBACAuthorizer

Add the following to `server.properties` file:

    authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer
    principal.builder.class=io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder

#### Configuring the KeycloakRBACAuthorizer

All the configuration properties for KeycloakRBACAuthorizer begin with a `strimzi.authorization.` prefix.

The token endpoint used by KeycloakRBACAuthorizer has to be the same as the one used for authentication:
- `strimzi.authorization.token.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/token" - the endpoint used to exchange the access token for a list of grants)
- `strimzi.authorization.client.id` (e.g.: "kafka" - the client representing a Kafka Broker which has Authorization Services enabled)

You may also want to configure some other things. You may want to set a logical cluster name so you can target it with authorization rules:
- `strimzi.authorization.kafka.cluster.name` (e.g.: "dev-cluster" - a logical name of the cluster which can be targeted with authorization services resource definitions, and permission policies)

You can integrate KeycloakRBACAuthorizer with SimpleAclAuthorizer:
- `strimzi.authorization.delegate.to.kafka.acl` (e.g.: "true" - if enabled, then when action is not granted based on Keycloak Authorization Services grant it is delegated to SimpleACLAuthorizer which can still grant it.)

If you turn on authorization support in Kafka brokers, you need to properly set `super.users` property. 
By default, access token's 'sub' claim is used as user id.
You may want to use another claim provided in access token as an alternative user id (username, email ...). 

For example, to add the account representing Kafka Broker in Keycloak to `super.users` add the following to your `server.properties` file:

    super.users=User:service-account-kafka

This assumes that you configured alternative user principal extrantion from the token by adding to JAAS configuration the parameter:

    oauth.username.claim="preferred_username"

When using TLS to connect to Keycloak (which you always should in the production environment) you may need to configure the truststore.
You can see how to configure that in [Configuring the TLS truststore](#configuring-the-tls-truststore) chapter.
Use analogous properties except that they should start with `strimzi.authorization.` rather than `oauth.`

For a more in-depth guide to using Keycloak Authorization Services see [the tutorial](examples/README-authz.md).

Configuring the Kafka client
----------------------------

Configuring the Kafka client is very similar to configuring the Kafka broker.
Clients don't have multiple listeners so there is one authentication configuration, which makes things slightly simpler.
It is more common on the client to compose configuration properties programmatically rather than reading in a properties file (like `server.properties`).

### <a name="enabling-sasl-oauthbearer-mechanism"></a> Enabling SASL_OAUTHBEARER mechanism

In order to use insecure connectivity set the following property:

    security.protocol=SASL_PLAINTEXT
    
If you want to use secure connectivity (using TLS) set:

    security.protocol=SASL_SSL

Then enable OAUTHBEARER mechanism:

    sasl.mechanism=OAUTHBEARER

### <a name="configuring-the-jaas-login-module-client"></a> Configuring the JAAS login module

Set the `sasl.jaas.config` property with JAAS configuration value that provides OAUTHBEARER handling, and configures Strimzi Kafka OAuth:
```
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.token.endpoint.uri="https://server/token-endpoint" ;
```

Here we specified the `oauth.token.endpoint.uri` configuration key and its value as a JAAS configuration parameter. 

A value of the `sasl.jaas.config` property always starts with:
`org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required `

Followed by zero or more configuration parameters, separated by a whitespace, and ended with a semicolon - `;`

### <a name="enabling-the-custom-callbacks-client"></a>Enabling the custom callbacks

Install the Strimzi Kafka OAuth login callback:

    sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler

### <a name="configuring-the-oauth2-client"></a>Configuring the OAuth2

The `oauth.token.endpoint.uri` property always has to be specified. 
Its value points to OAuth2 Token Endpoint provided by authorization server.

Strimzi Kafka OAuth supports three ways to configure authentication on the client.
The first is to specify the clientId and clientSecret configured on the authorization server specifically for the individual client deployment.

This is achieved by specifying the following:
- `oauth.client.id` (e.g.: "my-client")
- `oauth.client.secret` (e.g.: "my-client-secret")

When client starts to establish the connection with the Kafka Broker it will first obtain an access token from the configured Token Endpoint, authenticating with the configured clientId and clientSecret using client_credentials grant type.

The second way is to manually obtain and set a refresh token:

- `oauth.refresh.token`

When using this approach you are not limited to OAuth2 client_credentials grant type for obtaining a token.
You can use a password grant type and authenticate as an individual user, rather than a client application.
There is a [simple CLI tool](examples/docker/kafka-oauth-strimzi/kafka/oauth.sh) you can use to obtain the refresh token or an access token. 

When client starts to establish the connection with the Kafka Broker it will first obtain an access token from the configured Token Endpoint, using refresh_token grant type for authentication.

The third way is to manually obtain and set an access token:

- `oauth.access.token`

Access tokens are supposed to be short-lived in order to prevent unauthorized access if the token leaks.
It is up to you, your environment, and how you plan to run your Kafka client application to consider if using long-lived access tokens is appropriate.

For debug purposes you may want to properly configure which JWT token attribute contains the username of the account used to obtain the access token:

- `oauth.username.claim` (e.g.: "preferred_username")

This does not affect how Kafka client is presented to the Kafka Broker.
The broker performs username extraction from the token once again.

You may want to explicitly specify the period the access token is considered valid.
This may be necessary if using opaque tokens do not contain expiry info. 
This also allows you to shorten the token's lifespan.

On the client the access token is reused for multiple connections with the Kafka Broker.
Before it expires the token is refreshed in the background so that a valid token is always available for all the connections.
You can make the token refresh more often than strictly necessary based on when it expires:

- `oauth.max.token.expiry.seconds` (e.g.: "600" - set token expiry to 10 minutes)  

If expiry is set to more that actual token expiry, this setting will have no effect.
Note that this does not make any change to the token itself - the original token is still passed to the server.

There are a few other settings that control the access token refresh behaviour.
You can read about them in [Kafka Documentation](https://kafka.apache.org/documentation/).
The properties of interest are:
- `sasl.login.refresh.buffer.seconds`
- `sasl.login.refresh.min.period.seconds`
- `sasl.login.refresh.window.factor`
- `sasl.login.refresh.window.jitter`

If you have DEBUG logging turned on for `io.strimzi`, and are using opaque (non JWT) tokens, you can avoid parsing error warnings in the logs by specifying:

- `oauth.access.token.is.jwt` (e.g.: "false")

When setting this to `false` the client library will not attempt to parse and introspect the token as if it was JWT.

### Client config example

Here's an example of a complete `my.properties` file which you can load in your client as `java.util.Properties` object, and pass to `KafkaProducer` or `KafkaConsumer`.

```
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.client.id="team-a-client" \
  oauth.client.secret="team-a-client-secret" \
  oauth.token.endpoint.uri="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
```

When you have a Kafka Client connecting to a single Kafka cluster it only need one set of credentials - in such a situation it is sometimes more convenient to just use ENV vars.
In that case you could simplify `my.properties` file:

```
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
```

And pass the configuration as ENV vars:
```
export OAUTH_CLIENT_ID="team-a-client"
export OAUTH_CLIENT_SECRET="team-a-client-secret"
export OAUTH_TOKEN_ENDPOINT_URI="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token"
```

Note that if you have JAAS config parameters with the same names (lowercase with dots) they would not take effect - ENV vars will override them.

Configuring the TLS truststore
------------------------------

When your application connects to your authorization server, it should always use a secure connection - `https://`.
That goes for the Kafka Brokers, as well as for the Kafka clients.
 
You may need to provide a custom truststore for connecting to your authorization server or may need to turn off certificate hostname verification.

Use the following configuration properties to configure a truststore:
- `oauth.ssl.truststore.location` (e.g.: "/path/to/truststore.p12")
- `oauth.ssl.truststore.password` (e.g.: "changeit")
- `oauth.ssl.truststore.type` (e.g.: "pkcs12")      

You may want to explicitly set the random number implementation provider to use a non-default one:
- `oauth.ssl.secure.random.implementation` (e.g.: "SHA1PRNG")

If you need to turn off certificate hostname verification set the following property to empty string:
- `oauth.ssl.endpoint.identification.algorithm` (e.g. "") 

These configuration properties can be used to configure truststore for `KeycloakRBACAuthorizer` as well, but they have to be prefixed with `strimzi.authorization.` instead of `oauth.` (e.g.: `strimzi.authorization.ssl.truststore.location`).  

Demo
----

For a demo / tutorial covering OAuth2 authentication see [examples README](examples/README.md).

For another demo / tutorial covering token based authorization using Keycloak Authorization Services see [authorization README](examples/README-authz.md)

