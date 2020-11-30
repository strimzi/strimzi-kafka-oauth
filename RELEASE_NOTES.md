Release Notes
=============

0.7.0
-----

### OAuth over PLAIN

SASL_PLAIN can now be used to perform the authentication using a service account clientId and secret or a long-lived access token.

See [README.md] for instructions on how to set up the brokers and the clients.

### Audience check

Additional server-side configuration option was added to enable / disable the audience check:
* `oauth.check.audience` (e.g. "true") 


0.6.0
-----

### Optimized internals

Redundant multiple instances of validators were being instantiated resulting in service components, supposed to be singletons, to be instantiated multiple times.
That was fixed through internal refactoring.

### Improved server-side logging

Improvements have been made to logging for easier tracking of the requests, and better error reporting. 

### Improved handling of expired or otherwise invalidated access tokens

Many improvements have been made to address problems with access tokens expiring, or becoming invalid.

#### Fixed, and documented the re-authentication support

To prevent active sessions operating beyond the access token lifetime, Kafka 2.2 and later has [re-authentication support](https://cwiki.apache.org/confluence/display/KAFKA/KIP-368%3A+Allow+SASL+Connections+to+Periodically+Re-Authenticate), which has to be explicitly enabled.
Use the following `server.properties` configuration to enable re-authentication, and force clients to re-authenticate within one hour:

    connections.max.reauth.ms=3600000

If the access token expires before that, the re-authentication will be enforced within the access token lifetime.
Any non-authentication operation after token expiry will cause the connection to be terminated by the broker.

Re-authentication should be enabled if you want to prevent authenticated sessions from continuing beyond access token expiry.
Also, without re-authentication enabled, the `KeycloakRBACAuthorizer` will now deny further access as soon as the access token expires. 
It would in any case fail fairly quickly as it relies on a valid access token when refreshing the list of grants for the current session.
  
In previous versions the re-authentication support was broken due to [the bug #60](https://github.com/strimzi/strimzi-kafka-oauth/pull/60).
That should now be fixed.

#### Added OAuthSessionAuthorizer

Re-authentication forces active sessions whose access tokens have expired to immediately become invalid. 
However, if for some reason you don't want to, or can't use re-authentication, and you don't use `KeycloakRBACAuthorizer`, but still want to enforce session expiry, or if you simply want to better log if token expiry occurs, the new authorizer denies all actions after the access token of the session has expired.
The client will receive the `org.apache.kafka.common.errors.AuthorizationException`.

Usage of `OAuthSessionAuthorizer` is optional.
It 'wraps' itself around another authorizer, and delegates all calls after determining that the current session still contains a valid token.
This authorizer should *not* be used together with `KeycloakRBACAuthorizer`, since the latter already performs all the same checks.

Two configuration options have been added for use by this authorizer:

* `strimzi.authorizer.delegate.class.name`

  Specifies the delegate authorizer class name to be used.
  
* `strimzi.authorizer.grant.when.no.delegate`

  Enables this authorizer to work without the delegate.

#### Deprecated the JwtKafkaPrincipalBuilder in favor of the new OAuthKafkaPrincipalBuilder

In order to support the newly added `OAuthSessionAuthorizer` the `JwtKafkaPrincipalBuilder` had to be moved to `oauth-server` module, which called for a different package.
We took the opportunity to also give the class a better name. The old class still exists, but simply extends the new class.

To use the new class your `server.properties` configuration should contain:

    principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
 
#### KeycloakRBACAuthorizer improvements

* Added session expiry enforcement to KeycloakRBACAuthorizer. 
If the access token used during authentication expires all the authorizations will automatically be denied.
Note, that if re-authentication is enabled, and properly functioning, the access token should be refreshed on the server before ever expiring.

* The KeycloakRBACAuthorizer will now regularly refresh the list of grants for every active session, and thus allows any permissions changes made at authorization server (Keycloak / RH-SSO) to take effect on Kafka brokers.  
For example, if the access token expires in 30 minutes, and grants are refreshed every minute, the revocation of grants will be detected within one minute, and immediately enforced. 

Additional `server.properties` configuration options have been introduced:

* `strimzi.authorization.grants.refresh.period.seconds`

  The time between two grants refresh job runs. 
  The default value is 60 seconds. If this value is set to 0 or less, refreshing of grants is turned off.

* `strimzi.authorization.grants.refresh.pool.size`

  The number of threads that can fetch grants in parallel.
  The default value is 5.
  
#### Introduced fast JWKS keys refresh

When using fast local signature validation using JWKS endpoint keys, if signing keys are suddenly revoked at the authorization server, it takes a while for the Kafka broker to be aware of this change.
Until then, the Kafka broker keeps successfully authorizing new connections with access tokens signed using old keys.
At the same time it rejects newly issued access tokens that are properly signed with the new signing keys which Kafka broker doesn't yet know about.
In order to shorten this mismatch period as much as possible, the broker will now trigger JWKS keys refresh as soon as it detects a new signing key.
And it will keep trying if not successful using a so called exponential back-off, where after the unsuccessful attempt it waits a second, then two, then 4, 8, 16, 32, ... 
It will not flood the server with requests, always pausing for a minimum time between two consecutive keys refresh attempts.

While there would still be a few `invalid token errors`, the clients, if coded correctly, to reinitialise the KafkaProducer / KafkaConsumer in order to force access token refresh, should quickly recover.

The following new configuration option has been introduced:

* `oauth.jwks.refresh.min.pause.seconds`

  The minimum pause between two consecutive reload attempts. This prevents flooding the authorization server.
  The default value is 1 second.

#### Fixed exception types thrown during token validation

In some cases the client would not receive `org.apache.kafka.common.errors.AuthenticationException` when it should.
Error messages were also improved to give a more precise reason for the failure.


0.5.0
-----

### Improved compatibility with authorization servers

Some claims are no longer required in token or Introspection Endpoint response (`iat`).
Others can be configured to not be required:
* `iss` claim is not required if `oauth.check.issuer` is set to `false`
* `sub` claim is no longer required if `oauth.username.claim` is configured since then it is no longer used to extract principal. 

Additional options were added to improve interoperability with authorization servers.

The following options were added:

* `oauth.scope`

  Scope can now be specified for the Token endpoint on the Kafka clients and on the Kafka broker for inter-broker communication.

* `oauth.check.issuer`

  Issuer check can now be disabled when configuring token validation on the Kafka broker - some authorization servers don't provide `iss` claim.
  
* `oauth.fallback.username.claim`

  Principal can now be extracted from JWT token or Introspection endpoint response by using multiple claims.
  First `oauth.username.claim` is attempted (if configured). If the value is not present, the fallback claim is attempted.
  If neither `oauth.username.claim` nor `oauth.fallback.username.claim` is specified or its value present, `sub` claim is used.

* `oauth.fallback.username.prefix`

  If principal is set by `oauth.fallback.username.claim` then its value will be prefixed by the value of `oauth.fallback.username.prefix`, if specified.

* `oauth.userinfo.endpoint.uri`

  Sometimes the introspection endpoint doesn't provide any claim that could be used for the principal. In such a case User Info Endpoint can be used, and configuration of `oauth.username.claim`, `oauth.fallback.username.claim`, and `oauth.fallback.username.prefix` is taken into account.

* `oauth.valid.token.type`

  When using the Introspection Endpoint, some servers use custom values for `token_type`.
  If this configuration parameter is set then the `token_type` attribute has to be present in Introspection Token response, and has to have the specified value.

### Fixed JWKS keys refresh bug

The job that refreshes the keys would be cancelled if fetching of keys failed due to network error or authorization server glitch.

### Fixed a non-standard `token_type` enforcement when using the Introspection Endpoint

If `token_type` was present it was expected to be equal to `access_token` which is not an OAuth 2.0 spec compliant value.
Token type check is now disabled unless the newly introduced `oauth.valid.token.type` configuration option is set. 

### Improved examples

* Fixed an issue with `keycloak` and `hydra` containers not visible when starting services in separate shells.

  The instructions for running `keycloak` / `hydra` separately omitted the required `-f compose.yml` as a first compose file, resulting in a separate bridge network being used.

* Added Spring Security Authorization Server

### Improved logging to facilitate troubleshooting

There is now some TRACE logging support which should only ever be used in development / testing environment because it outputs secrets into the log.
When integrating with your authorization server, enabling TRACE logging on `io.strimzi.kafka.oauth` logger will output the authorization server responses which can point you to how to correctly configure `oauth.*` parameters to make the integration work. 

### Bumped keycloak-core library version

The helper library used for JWT / JWKS handling was bumped to version 10.0.0

0.4.0
-----

### Deprecated configuration options

The following configuration options have been deprecated:
* `oauth.tokens.not.jwt` is now called `oauth.access.token.is.jwt` and has a reverse meaning.
* `oauth.validation.skip.type.check` is now called `oauth.check.access.token.type` and has a reverse meaning.


See: Align configuration with Kafka Operator PR ([#36](https://github.com/strimzi/strimzi-kafka-oauth/pull/36)).

### Compatibility improvements

Scope claim is no longer required in an access token. ([#30](https://github.com/strimzi/strimzi-kafka-oauth/pull/30))
That improves compatibility with different authorization servers, since the attribute is not required by OAuth 2.0 specification neither is it used by validation logic.

### Updated dependencies

`jackson-core`, and `jackson-databind` libraries have been updated to latest versions. ([#33](https://github.com/strimzi/strimzi-kafka-oauth/pull/33))

### Instructions for developers added

Instructions for preparing the environment, building and deploying the latest version of Strimzi Kafka OAuth library with Strimzi Kafka Operator have been added.

See: Hacking on OAuth and deploying with Strimzi Kafka Operator PR ([#34](https://github.com/strimzi/strimzi-kafka-oauth/pull/34))

### Improvements to examples and documentation

Fixed enabled remote debugging mode in example `compose-authz.yml` ([#39](https://github.com/strimzi/strimzi-kafka-oauth/pull/39))

0.3.0
-----

### Token-based authorization with Keycloak Authorization Services

It is now possible to use Keycloak Authorization Services to centrally manage access control to resources on Kafka Brokers ([#24](https://github.com/strimzi/strimzi-kafka-oauth/pull/24))
See the [tutorial](examples/README-authz.md) which explains many concepts.
For configuration details also see [KeycloakRBACAuthorizer JavaDoc](oauth-keycloak-authorizer/src/main/java/io/strimzi/kafka/oauth/server/authorizer/KeycloakRBACAuthorizer.java). 

### ECDSA signature verification support

The JWTSignatureValidator now supports ECDSA signatures, but requires explicit enablement of BouncyCastle security provider ([#25](https://github.com/strimzi/strimzi-kafka-oauth/pull/25))
To enable BouncyCastle set `oauth.crypto.provider.bouncycastle` to `true`.
Optionally you may control the order where the provider is installed by using `oauth.crypto.provider.bouncycastle.position` - by default it is installed at the end of the list of existing providers.

0.2.0
-----

### Testsuite with integration tests

A testsuite based on Arquillian Cube, and using docker containers was added.
 
### Examples improvements

Added Ory Hydra authorization server to examples. 

0.1.0
-----

### Initial OAuth 2 authentication support for Kafka

Support for token-based authentication that plugs into Kafka's SASL_OAUTHBEARER mechanism to provide:
* Different ways of access token retrieval for Kafka clients (clientId + secret, refresh token, or direct access token)
* Fast signature-checking token validation mechanism (using authorization server's JWKS endpoint)
* Introspection based token validation mechanism (using authorization server's introspection endpoint)

See the [tutorial](examples/README.md).
