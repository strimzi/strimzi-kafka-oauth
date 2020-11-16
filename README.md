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
    - [Enabling the re-authentication](#enabling-the-re-authentication)
    - [Enforcing the session timeout](#enforcing-the-session-timeout)  
  - [Configuring the Kafka Broker authorization](#configuring-the-kafka-broker-authorization)
    - [Enabling the KeycloakRBACAuthorizer](#enabling-the-keycloakrbacauthorizer)
    - [Configuring the KeycloakRBACAuthorizer](#configuring-the-keycloakrbacauthorizer)
    - [Configuring the RBAC rules through Keycloak Authorization Services](#configuring-the-rbac-rules-through-keycloak-authorization-services)
- [Configuring the Kafka client with SASL_OAUTHBEARER](#configuring-the-kafka-client-with-sasl_oauthbearer)
  - [Enabling SASL_OAUTHBEARER mechanism](#enabling-sasl_oauthbearer-mechanism)
  - [Configuring the JAAS login module](#configuring-the-jaas-login-module-client)
  - [Enabling the custom callbacks](#enabling-the-custom-callbacks-client)
  - [Configuring the OAuth2](#configuring-the-oauth2-client)
  - [Configuring the re-authentication on the client](#configuring-the-re-authentication-on-the-client)
  - [Client config example](#client-config-example)
  - [Handling expired or invalid tokens gracefully](#handling-expired-or-invalid-tokens-gracefully)
- [Configuring the Kafka client with SASL_PLAIN](#configuring-the-kafka-client-with-sasl_plain)
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
    oauth-server-plain/target/kafka-oauth-server-plain-*.jar
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

If you use the KeycloakRBACAuthorizer for authorization, then you have to use Keycloak or Keycloak based authorization server to configure security policies and permissions for users and service accounts.
 
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

Since version 0.7.0 there is also support for so called 'OAuth over PLAIN' which allows using the SASL_PLAIN mechanism to authenticate with an OAuth access token or with a clientId and a secret.
In order to use that you have to enable the SASL_PLAIN mechanism as well (you can enable one or the other or both):

    sasl.enabled.mechanisms=OAUTHBEARER,PLAIN

#### Configuring the JAAS login module

In JAAS configuration we do three things:
- Activate a specific JAAS login module - for Strimzi Kafka OAuth that is either:
  - the `org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule` class which implements Kafka's SASL_OAUTHBEARER authentication mechanism, or 
  - the `org.apache.kafka.common.security.plain.PlainLoginModule` class which implements Kafka's SASL_PLAIN authentication mechanism.
- Activate the custom server callback that will provide server-side token validation:
  - For `SASL_OAUTHBEARER` the callback class should be `io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler`.
  - For `SASL_PLAIN` the callback class should be `io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler`.
- Specify the configurations used by the login module, and by our custom extensions - the server callback handler and / or the login callback handler (which we will do in [the next step](#enabling-the-custom-callbacks)).

JAAS configuration can be specified inside `server.properties` file using the listener-scoped `sasl.jaas.config` key. 
Assuming there is the `CLIENT` listener configured as shown above, we can specify configuration specifically for that listener:

```
listener.name.client.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
    oauth.jwks.endpoint.uri="https://server/keys" ;
```

Note the `listener.name.client.oauthbearer.` prefix for the key. The word `client` in the key refers to the `CLIENT` listener. 
In this case we are configuring the validator with fast local signature check that uses the JWKS endpoint provided by authorization server.

The `oauthbearer` part refers to the sasl mechanism SASL_OAUTHBEARER on the `CLIENT` listener. In order to configure the SASL_PLAIN mechanism you have to use `plain` instead:

```
listener.name.client.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required \
    oauth.jwks.endpoint.uri="https://server/keys" oauth.token.endpoint.uri="https://server/token";
```

Here we additionally specified the `oauth.token.endpoint.uri` configuration key which is the only additional configuration option required for SASL_PLAIN. The other options are the same as for SASL_OAUTHBEARER.
Any Strimzi Kafka OAuth keys that begin with `oauth.` can be specified this way - scoped to the individual listener.

A value of the scoped `sasl.jaas.config` always starts with the JAAS login module name, followed by `required`, and then followed by zero or more configuration parameters, separated by a whitespace, and ended with a semicolon - `;`
Inside `server.properties` this has to be a single line, or if multi-line, each non-final line has to be ended with a backslash `\`.

#### Enabling the custom callbacks

The custom callbacks are enabled per listener in `server.properties` using the listener-scoped configuration.

On the Kafka Broker we typically only install the custom server callback - the so called `validator` callback handler.

An example for SASL_OAUTHBEARER:

    listener.name.client.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler

An example for SASL_PLAIN:

    listener.name.client.plain.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler


If the SASL_OAUTHBEARER listener is also used for interbroker communication, then you also have to configure the client callback handler class.

    listener.name.client.oauthbearer.sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler 

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

Some authorization servers don't provide the `iss` claim. In that case you would not set `oauth.valid.issuer.uri`, and you would explicitly turn off issuer checking by setting the following option to `false`:
- `oauth.check.issuer` (e.g. "false")

JWT tokens contain unique user identification in `sub` claim. However, this is often a long number or a UUID, but we usually prefer to use human readable usernames, which may also be present in JWT token.
Use `oauth.username.claim` to map the claim (attribute) where the value you want to use as user id is stored:
- `oauth.username.claim` (e.g.: "preferred_username")

If `oauth.username.claim` is specified the value of that claim is used instead, but if not set, the automatic fallback claim is the `sub` claim.

You can specify the secondary claim to fallback to, which allows you to map multiple account types into the same principal namespace: 
- `oauth.fallback.username.claim` (e.g.: "client_id")
- `oauth.fallback.username.prefix` (e.g.: "client-account-")

If `oauth.username.claim` is specified but value does not exist in the token, then `oauth.fallback.username.claim` is used. If value for that doesn't exist either, the exception is thrown.
When `oauth.fallback.username.prefix` is specified and the claim specified by `oauth.fallback.username.claim` contains a non-null value the resulting user id will be equal to concatenation of the prefix, and the value.

For example, if the following configuration is set:

    oauth.username.claim="username"
    oauth.fallback.username.claim="client_id"
    oauth.fallback.username.prefix="client-account-"

Then, if the token contains `"username": "alice"` claim then the principal will be `User:alice`.
Otherwise, if the token contains `"client_id": "my-producer"` claim then the principal will be `User:client-account-my-producer`. 

If your authorization server uses ECDSA encryption then you need to enable the BouncyCastle JCE crypto provider:
- `oauth.crypto.provider.bouncycastle` (e.g.: "true")

Depending on your authorization server you may need to relax some checks: 
- `oauth.check.access.token.type` (e.g.: "false" - do not require `"typ": "Bearer"` in JWT token)

You can control how often the keys used for signature checks are refreshed and when they expire:
- `oauth.jwks.refresh.seconds` (e.g.: "300" - that's the default value - keys are refreshed every 5 minutes)
- `oauth.jwks.expiry.seconds` (e.g.: "360" - that's the default value - keys expire 6 minutes after they are loaded)

If an access token signed with an unknown signing key is encountered, another refresh is scheduled immediately.
You can control the minimum pause between two consecutive scheduled keys refreshes - the default is 1 second:
- `oauth.jwks.refresh.min.pause.seconds` (e.g.: "0" - no minimium pause)

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

Some authorization servers don't provide the `iss` claim. In that case you would not set `oauth.valid.issuer.uri`, and you would explicitly turn off issuer checking by setting the following option to `false`:
- `oauth.check.issuer` (e.g.: "false")

By default, if the Introspection Endpoint response contains `token_type` claim, there is no checking performed on it.
Some authorization servers use a non-standard `token_type` value. To give the most flexibility, you can specify the valid `token_type` for your authorization server:
- `oauth.valid.token.type` (e.g.: "access_token")

When this is specified the Introspection Endpoint response has to contain `token_type`, and its value has to be equal to the value specified by `oauth.valid.token.type`.

Introspection Endpoint may or may not return identifying information which we could use as user id to construct a principal for the user.

If the information is available we attempt to extract the user id from Introspection Endpoint response.

Use `oauth.username.claim` to map the attribute where the user id is stored:
- `oauth.username.claim` (e.g.: "preferred_username")

You can fallback to a secondary attribute, which allows you to map multiple account types into the same user id namespace: 
- `oauth.fallback.username.claim` (e.g.: "client_id")
- `oauth.fallback.username.prefix` (e.g.: "client-account-")

If `oauth.username.claim` is specified but value does not exist in the Introspection Endpoint response, then `oauth.fallback.username.claim` is used. If value for that doesn't exist either, the exception is thrown.
When `oauth.fallback.username.prefix` is specified and the attribute specified by `oauth.fallback.username.claim` contains a non-null value the resulting user id will be equal to concatenation of the prefix, and the value.

If none of the `oauth.*.username.*` attributes is specified, `sub` claim will be used automatically.

For example, if the following configuration is set:

    oauth.username.claim="username"
    oauth.fallback.username.claim="client_id"
    oauth.fallback.username.prefix="client-account-"

It means that if the response contains `"username": "alice"` then the principal will be `User:alice`.
Otherwise, if the response contains `"client_id": "my-producer"` then the principal will be `User:client-account-my-producer`. 

Sometimes the Introspection Endpoint does not provide any useful identifying information that we can use for the user id.
In that case you can configure User Info Endpoint:
 
- `oauth.userinfo.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/userinfo")

If the user id could not be extracted from Introspection Endpoint response, then the same rules (`oauth.username.claim`, `oauth.fallback.username.claim`, `oauth.fallback.username.prefix`) will be used to try extract the user id from User Info Endpoint response.

When you have a DEBUG logging configured for the `io.strimzi` category you may need to specify the following to prevent warnings about access token not being JWT:
- `oauth.access.token.is.jwt` (e.g.: "false")

##### Configuring the client side of inter-broker communication

All the Kafka Brokers in the cluster should be configured with the same clientId and clientSecret, and the corresponding user should be added to `super.users` since inter-broker client requires super-user permissions.

Specify the following `oauth.*` properties:
- `oauth.token.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/token")
- `oauth.client.id` (e.g.: "kafka" - this is the client configuration id for Kafka Broker)
- `oauth.client.secret` (e.g.: "kafka-secret")
- `oauth.username.claim` (e.g.: "preferred_username")

Also specify the principal corresponding to the client account identified by `oauth.client.id` in `super.users` property in `server.properties` file:
- `super.users` (e.g.: "User:service-account-kafka") 

This is not a full set of available `oauth.*` properties. All the `oauth.*` properties described in the next chapter about [configuring the Kafka clients](#configuring-the-kafka-client) also apply to configuring the client side of inter-broker communication. 

#### Enabling the re-authentication

Access tokens expire after some time. The token validation for the purpose of session authentication is only performed immediately after the new connection from the client is established. 
If using SimpleACLAuthorizer or no authorizer at all, then there is no further need for the access token after the authentication, and by default the expiry of the token will not result in session closure or denial of access within the existing session.

Since Kafka version 2.2 the Kafka brokers support a [re-authentication mechanism](https://cwiki.apache.org/confluence/display/KAFKA/KIP-368%3A+Allow+SASL+Connections+to+Periodically+Re-Authenticate) allowing clients to update the token mid-session, without having to drop and re-establish the connection. 
When a client sends the new access token, validation is performed on the broker as if a new connection was established.

The re-authentication only works with SASL_OAUTHBEARER mechanism, and is disabled by default in order to prevent the old clients that don't understand the new Kafka wire protocol from breaking.
In order to enable the re-authentication on the Kafka broker, use the `connections.max.reauth.ms` property in `server.properties`:

    connections.max.reauth.ms=3600000

In this example the maximum time until next re-authentication is set to one hour.
If the access token expires sooner than that, the re-authentication will be triggered sooner.

The option can be specified per-listener. For example if you have a listener called `CLIENTS`, you can specify:

    listener.name.clients.oauthbearer.connections.max.reauth.ms=3600000

#### Enforcing the session timeout 

If re-authentication is enabled, the session timeout is enforced as the expiry time of the access token. 
By using re-authentication the multiple 'lightweight' sessions can follow one another over the same network connection for as long as the connection isn't closed or interrupted due to processes restarting or due to network issues. 

If for some reason you can't enable re-authentication or don't want to use it, and if you want to invalidate the session when access token expires, but aren't using `KeycloakRBACAuthorizer`, which does this automatically (since version 0.6.0 of this library), you can use the `OAuthSessionAuthorizer` to enforce token expiry mid-session.

`OAuthSessionAuthorizer` works by checking the access token expiry on every operation performed, and denies all access after the token has expired.
As long as the token has not yet expired (it may have been recently invalidated at authorization server but the Kafka broker may not yet know about it) the authorization is delegated to the delegate authorizer.

If you want to install OAuthSessionAuthorizer wrapped around Simple ACL Authorizer install it as follows in `server.properties`:

    authorizer.class.name=io.strimzi.kafka.oauth.server.OAuthSessionAuthorizer
    principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
    
    strimzi.authorizer.delegate.class.name=kafka.security.auth.SimpleAclAuthorizer

You configure the `SimpleAclAuthorizer` by specifying the same properties as if it was installed under `authorizer.class.name`.

It's the same for any other authorizer you may use - instead of using `authorizer.class.name` you install it by using `strimzi.authorizer.delegate.class.name`.

Do not use `OAuthSessionAuthorizer` together with `KeycloakRBACAuthorizer` as that would be redundant.

If you don't use any authorizer at all, and don't use re-authentication, but still want to enforce access token expiry mid-session, don't specify the `strimzi.authorizer.delegate.class.name` at all.
Instead, specify the following configuration:

    strimzi.authorizer.grant.when.no.delegate=true

In this case, unless the access token has expired, all the actions will be granted. The broker will behave as if no authorizer was installed, effectively turning every user into a 'super user'.
The unauthenticated users, or users authenticated with a mechanism other than OAuth will also automatically have all the actions granted.


### Configuring the Kafka Broker authorization

Strimzi Kafka OAuth provides support to centrally manage not only users and clients, but also permissions to Kafka broker resources - topics, consumer groups, configurations ...

Support for this works specifically with Keycloak Authorization Services.

By default, authorization is not enabled on Kafka Broker. There is `kafka.security.auth.SimpleAclAuthorizer` that comes with Kafka out-of-the-box, and is well documented in [Kafka Documentation](https://kafka.apache.org/documentation/). 

Strimzi Kafka OAuth provides an alternative authorizer - `io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer`.
`KeycloakRBACAuthorizer` uses the access token and the Token Endpoint of the same Keycloak realm used for OAuth2 authentication as a source of permission grants for the authenticated session.

#### Enabling the KeycloakRBACAuthorizer

Add the following to `server.properties` file:

    authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer
    principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder

Note: Since version 0.6.0 the `io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder` has been deprecated. Use the above configuration instead.

#### Configuring the KeycloakRBACAuthorizer

All the configuration properties for KeycloakRBACAuthorizer begin with a `strimzi.authorization.` prefix.

The token endpoint used by KeycloakRBACAuthorizer has to be the same as the one used for authentication:
- `strimzi.authorization.token.endpoint.uri` (e.g.: "https://localhost:8443/auth/realms/demo/protocol/openid-connect/token" - the endpoint used to exchange the access token for a list of grants)
- `strimzi.authorization.client.id` (e.g.: "kafka" - the client representing a Kafka Broker which has Authorization Services enabled)

The authorizer will regularly reload the list of grants for active sessions. By default it will do this once every minute.
You can change this period or turn it off for debugging reasons (by setting it to "0"):
- `strimzi.authorization.grants.refresh.period.seconds` (e.g.: "120" - the refresh job period in seconds)

The refresh job works by enumerating the active tokens and requesting the latest grants for each.
It does that by using a thread pool. You can control the size of the thread pool (how much parallelism you want), the default value is 5:
- `strimzi.authorization.grants.refresh.pool.size` (e.g.: "10" - the maximum of 10 parallel fetches of grants at a time)

A single client connection typically has a unique access token even though you could use the same access token for multiple connections. 
Thus, the number of active tokens is generally proportional to the number of client connections.
Also keep in mind that this is replicated across all Kafka brokers in the cluster, as they all have to be configured the same way.

You may also want to configure some other things. You may want to set a logical cluster name so you can target it with authorization rules:
- `strimzi.authorization.kafka.cluster.name` (e.g.: "dev-cluster" - a logical name of the cluster which can be targeted with authorization services resource definitions, and permission policies)

You can integrate KeycloakRBACAuthorizer with SimpleAclAuthorizer:
- `strimzi.authorization.delegate.to.kafka.acl` (e.g.: "true" - if enabled, then when action is not granted based on Keycloak Authorization Services grant it is delegated to SimpleACLAuthorizer which can still grant it.)

If you turn on authorization support in Kafka brokers, you need to properly set `super.users` property. 
By default, access token's `sub` claim is used as user id.
You may want to use another claim provided in access token as an alternative user id (username, email ...). 

For example, to add the account representing Kafka Broker in Keycloak to `super.users` add the following to your `server.properties` file:

    super.users=User:service-account-kafka

This assumes that you configured alternative user id extraction from the token by adding to JAAS configuration the parameter:

    oauth.username.claim="preferred_username"

When using TLS to connect to Keycloak (which you always should in the production environment) you may need to configure the truststore.
You can see how to configure that in [Configuring the TLS truststore](#configuring-the-tls-truststore) chapter.
Use analogous properties except that they should start with `strimzi.authorization.` rather than `oauth.`

For a more in-depth guide to using Keycloak Authorization Services see [the tutorial](examples/README-authz.md).

#### Configuring the RBAC rules through Keycloak Authorization Services

In order to grant Kafka permissions to users or service accounts you have to use the Keycloak Authorization Services rules on the OAuth client that represents the Kafka Broker - typically this client has `kafka` as its clientId.
The rules exist within the scope of this client, which means that if you have different Kafka clusters configured with different OAuth clientIds they would each have a separate set of permissions even though using the same set of users, and client accounts. 

When the Kafka client authenticates using SASL_OAUTHEARER or SASL_PLAIN configured as 'OAuth over PLAIN' the KeycloakRBACAuthorizer retrieves the list of grants for the current session from the Keycloak server using the access token of the current session.
This list of grants is the result of evaluating the Keycloak Authorization Services policies and permissions. 

There are four concepts used to grant permissions: `resources`, `authorization scopes`, `policies`, and `permissions`.

##### Authorization scopes

Typically the initial configuration involves uploading the authorization scopes which creates a list of all the possible actions that can be performed on all the types of a Kafka resources.
This step is performed only once, before defining any permissions. Alternatively, the authorization scopes can be added manually, but make sure to not introduce typos.

The following authorization scopes can be used, mirroring the Kafka security model: `Create`, `Write`, `Read`, `Delete`, `Describe`, `Alter`, `DescribeConfig`, `AlterConfig`, `ClusterAction`.

##### Resources
  
The next step is to create targetting resources. When creating a Keycloak Authorization Services `resource` a pattern matching syntax is used to target the permissions rule to Kafka topics, consumer groups, or clusters.

The general pattern is as follows: RESOURCE_TYPE:NAME_PATTERN 

There are five resource types: `Topic`, `Group`, `Cluster`, `TransactionalId`, `DelegationToken`. 
And there are two matching options: exact matching (when the pattern does not end with *), and prefix matching (when the pattern ends with *).

A few examples:

    Topic:my-topic
    Topic:orders-*
    Group:orders-*
    Cluster:*

In addition, the general pattern can be prefixed by another one of the format `kafka-cluster`:CLUSTER_NAME, followed by comma, where cluster name is the name configured to `KeycloakRBACAuthorizer` using `strimzi.authorization.kafka.cluster.name`.

For example:

    kafka-cluster:dev-cluster,Topic:*
    kafka-cluster:*,Group:b_*

When the `kafka-cluster` prefix is not present it is assumed to be `kafka-cluster:*`.

When the resource is defined a list of possible authorization scopes relevant to the resource should be added to the list of scopes.
Currently this needs to be added for each resource definition based on whatever actions make sense for the targeted resource type.

The Kafka security model understands the following actions on different resource types.

Topic:
  -  Write
  -  Read
  -  Describe  
  -  Create
  -  Delete
  -  DescribeConfigs
  -  AlterConfigs
  -  IdempotentWrite

Group:
  -  Read
  -  Describe  
  -  Delete

Cluster:
  -  Create
  -  Describe
  -  Alter
  -  DescribeConfigs
  -  AlterConfigs
  -  IdempotentWrite
  -  ClusterAction

TransactionalId:
  -  Describe
  -  Write

DelegationToken:
  - Describe

While you may add any `authorization scope` to any `resource`, only the supported actions will ever matter.

##### Policies

The 'policies' are used to target permissions to one or more user accounts. The targeting can refer to specific user or service accounts, it can refer to the realm roles or client roles, it can refer to user groups, and it can even use a JS rule and match client's IP address for example.

Each policy can be given a name, and can be reused to target multiple permissions to multiple resources.

##### Permissions

The 'permissions' are the final step where you pull together the policies, resources, and authorization scopes to grant access to one or more users.

Scope permissions should be used to grant fine-grained permissions to users.

Each policy should be descriptively named in order to make it very clear what permissions it grants to which users.

See [the authorization tutorial](examples/README-authz.md) to get a hands-on understanding of how to configure the permissions through Keycloak Authorization Services.


Configuring the Kafka client with SASL_OAUTHBEARER
--------------------------------------------------

Configuring the Kafka client is very similar to configuring the Kafka broker.
Clients don't have multiple listeners so there is one authentication configuration, which makes things slightly simpler.
It is more common on the client to compose configuration properties programmatically rather than reading in a properties file (like `server.properties`).

### Enabling SASL_OAUTHBEARER mechanism

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

Some authorization servers require that scope is specified:

- `oauth.scope`

Scope is sent to the Token Endpoint when obtaining the access token.

For debug purposes you may want to properly configure which JWT token attribute contains the user id of the account used to obtain the access token:

- `oauth.username.claim` (e.g.: "preferred_username")

This does not affect how Kafka client is presented to the Kafka Broker.
The broker performs user id extraction from the token once again or it uses the Introspection Endpoint or the User Info Endpoint to get the user id.

By default the user id on the Kafka client is obtained from `sub` claim in the token - only if token is JWT. 
Client side user id extraction is not possible when token is an opaque token - not JWT.

You may want to explicitly specify the period the access token is considered valid. This allows you to shorten the token's lifespan.

On the client the access token is reused for multiple connections with the Kafka Broker.
Before it expires the token is refreshed in the background so that a valid token is always available for all the connections.
You can make the token refresh more often than strictly necessary by shortening its lifespan:

- `oauth.max.token.expiry.seconds` (e.g.: "600" - set token expiry to 10 minutes)  

If expiry is set to more that actual token expiry, this setting will have no effect.
Note that this does not make any change to the token itself - the original token is still passed to the server.

If you have DEBUG logging turned on for `io.strimzi`, and are using opaque (non JWT) tokens, you can avoid parsing error warnings in the logs by specifying:

- `oauth.access.token.is.jwt` (e.g.: "false")

When setting this to `false` the client library will not attempt to parse and introspect the token as if it was JWT.

#### Configuring the re-authentication on the client

Java based clients using Kafka client library 2.2 or later will automatically perform re-authentication if the broker supports it.

There are several Kafka client properties that control how the client refreshes the access token on the client side, before re-authenticating, based on token's expiry:

- [sasl.login.refresh.buffer.seconds](https://kafka.apache.org/documentation/#sasl.login.refresh.buffer.seconds)   
- [sasl.login.refresh.min.period.seconds](https://kafka.apache.org/documentation/#sasl.login.refresh.min.period.seconds)
- [sasl.login.refresh.window.factor](https://kafka.apache.org/documentation/#sasl.login.refresh.window.factor)
- [sasl.login.refresh.window.jitter](https://kafka.apache.org/documentation/#sasl.login.refresh.window.jitter)

Also keep in mind that if configuring the client token by using `oauth.access.token` property (manually obtaining it first), there is no way to automatically refresh such a token, and thus the re-authentication will use the already expired token one more time, resulting in authentication failure, and closure of the connection.

Note, that client-side token refresh works independently from re-authentication in the sense that it refreshes the token as necessary based on token's expiry information.
Once the token is obtained by the client, it is cached and re-used for multiple authentication attempts possibly across multiple connections.
If AuthenticationException occurs, that does not trigger the token refresh on the client.
The only way to force the Java client library to perform token refresh is to re-initialise the KafkaProducer / KafkaConsumer. 

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

When you have a Kafka Client connecting to a single Kafka cluster it only needs one set of credentials - in such a situation it is sometimes more convenient to just use ENV vars.
In that case you could simplify `my.properties` file:

```
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
```

And pass additional configuration as ENV vars:
```
export OAUTH_CLIENT_ID="team-a-client"
export OAUTH_CLIENT_SECRET="team-a-client-secret"
export OAUTH_TOKEN_ENDPOINT_URI="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token"
```

Note that if you have JAAS config parameters with the same names (lowercase with dots) they would not take effect - ENV vars will override them.

### Handling expired or invalid tokens gracefully

When using the Apache Kafka Java client library, you can distinguish between exceptions that occur as a result of authentication or authorization issues, and other exceptions.

From the client's point of view there usually isn't much to do when the exception occurs but to either retry the operation or exit.

Inside the cloud deployment it is a valid reaction to simply exit the process and let the cloud infrastructure start a new instance of the client service.
But more often than not, a more effective strategy is to repeat the last operation again which can take advantage of the current state of the program loaded in the memory.

If there is a problem during authentication the client will receive the `org.apache.kafka.common.errors.AuthenticationException`.
Once the session is authenticated, and if some authorizer is configured, the failed authorization will result in the `org.apache.kafka.common.errors.AuthorizationException`.

In order to retry the operation you'll want to close the current producer or consumer, and create a new one from scratch in order to force the client to obtain a new access token.
You'll also maybe want to make a slight pause when this happens, to prevent flooding the authorization server with the token requests.

```
String topic = "my-topic";
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);

try {
    consumer.subscribe(Arrays.asList(topic));

    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
        for (ConsumerRecord<String, String> record : records) {
            System.out.println("Consumed message: " + record.value());
        }
    }
} catch (AuthenticationException | AuthorizationException e) {
    consumer.close();
    consumer = new KafkaConsumer<>(config);
} catch (InterruptedException e) {
    throw new RuntimeException("Interrupted while consuming a message!", e);
}
```

Similarly for asynchronous API:

```
Producer<String, String> producer = new KafkaProducer<>(props);

for (int i = 0; ; i++) {
    try {

        producer.send(new ProducerRecord<>(topic, "Message " + i))
                .get();

        System.out.println("Produced Message " + i);

    } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while sending!");
    } catch (ExecutionException e) {
        final Throwable cause = e.getCause(); 
        if (cause instanceof AuthenticationException
                || cause instanceof AuthorizationException) {
            producer.close();
            producer = new KafkaProducer<>(props);
        } else {
            throw new RuntimeException("Failed to send message: " + i, e);
        }
    }
}
```

Configuring the Kafka client with SASL_PLAIN
--------------------------------------------

There is no OAuth specific configuration that would be required on the client when authenticating to Kafka Broker with SASL_PLAIN mechanism.
The Kafka Broker has to have the SASL_PLAIN mechanism enabled and properly configured with `JaasServerOauthOverPlainValidatorCallbackHandler` validation callback handler. 

Then, the standard SASL_PLAIN configuration is used on the client with the following two options:
- the client can authenticate using the service account clientId and secret. Setting the `username` to the value of clientId, and setting the `password` to the value of client secret
- the client can authenticate using a long-lived access token obtained through a browser sign-in or through using `curl` or similar CLI tool to obtain the access token with `client credentials` or the `password` authentication, 
  then setting the `username` to `access-token` reserved word and setting `password` to the access token string. 

For example, when using the Kafka Client Java library the configuration might look like:

```
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required \
  username="team-a-client" \
  password="team-a-client-secret"
```

Note that when using SASL_PLAIN the credentials are actually sent to the Kafka Broker, which is not the case when SASL_OAUTHBEARER is used, when the client library contacts the OAuth2 authorization service first, to obtain the access token, and then only sends to the Kafka Broker the access token.
On the other hand, the client doesn't need to connect to OAuth2 authorization server first, and there is no need for additionally configuring truststore for TLS connectivity.

The great advantage of SASL_PLAIN is that it can be used by Kafka clients that have no ready-to-use SASL_OAUTHBEARER support - SASL_PLAIN can be used with any Kafka client tool.

For example, to connect with `kafkacat` you could run:

    kafkacat -b my-cluster-kafka-bootstrap:9092 -X security.protocol=SASL_PLAINTEXT -X sasl.mechanism=PLAIN -X sasl.username=team-a-client -X sasl.password="team-a-client-secret" -t a_topic -P



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
