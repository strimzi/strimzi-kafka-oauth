Release Notes
=============

0.16.0
------

### Using Kafka 4.0.0

Kafka 4.0.0 server-side libraries are built with Java 17 bytecode compatibility. The client libraries are still built with Java 11 bytecode compatibility.

### Java 17 required for building the project

Java 17 is now required for building the project libraries. The example clients and the testsuite can also run with Java 11.
All the components are built with Java 11 bytecode compatibility except `kafka-oauth-keycloak-authorizer` which requires Java 17 due to the dependency on server-side Kafka 4.0.0 libraries.

### Removed support for KeycloakAuthorizer ACL delegation in Zookeeper mode

`KeycloakAuthorizer` can be configured to delegate authorization decision to standard ACL authorizer provided by Kafka.
Since Zookeeper mode is no longer supported, the ACL authorizer delegation only works if the Kafka node runs in KRaft mode.
If `KeycloakAuthorizer` is deployed to Kafka running in Zookeeper mode, and `strimzi.authorization.delegate.to.kafka.acl` is set to `true`, the broker will fail to start.

Kafka 4.x users should upgrade to this OAuth version (0.16.0). Kafka 3.x users can also use this OAuth version in both Kraft or Zookeeper mode, but if they use `KeycloakAuthorizer` with ACL delegation, that will not work in Zookeeper mode.

### Added a test and a fix for 'Overflow parsing timestamps in oauth JWTs as 32 bit int'

See [#260](https://github.com/strimzi/strimzi-kafka-oauth/issues/260)

0.15.0
------

### Added OAuth Client Assertion support

Allows clients to authenticate to authorization server by using client assertion as specified by https://www.rfc-editor.org/rfc/rfc7523 and https://www.rfc-editor.org/rfc/rfc7521.
The assertion can be provided by an external mechanism and available as a file on the file system or it can be explicitly set through OAuth configuration before running the Kafka client.

Introduced the following new configuration options:
- `oauth.client.assertion`
- `oauth.client.assertion.location`
- `oauth.client.assertion.type`

See [PR 211](https://github.com/strimzi/strimzi-kafka-oauth/pull/211)

### Added support for clients to read access token and refresh token from a file when authenticating

Introduced the following new configuration options:
- `oauth.refresh.token.location`
- `oauth.access.token.location`

See [PR 211](https://github.com/strimzi/strimzi-kafka-oauth/pull/211)

### Added support for bearer token authentication when connecting to protected authorization server endpoints

This is used by broker when connecting to JWKS and Introspection endpoints. Added to support talking to the Kubernetes API server's JWKS endpoint.

Introduced the following new configuration options:
- `oauth.server.bearer.token`
- `oauth.server.bearer.token.location`

The authentication configuration rules for configuring the introspection endpoint have been relaxed. 
Introspection endpoint can now be unprotected (no authentication configured on the listener) or it can be protected with 
`oauth.client.id` and `oauth.client.secret` to send `Basic` `Authorization` header or with the `oauth.server.bearer.token` or 
`oauth.server.bearer.token.location` when sending `Bearer` `Authorization` header.

JWKS endpoint can now also be protected in the same way.

See [PR 217](https://github.com/strimzi/strimzi-kafka-oauth/pull/217)

### Fixed NullPointerException that occurred when OAuthKafkaPrincipalBuilder was used with Kerberos authentication

See [PR 207](https://github.com/strimzi/strimzi-kafka-oauth/pull/207)

### Fixed a user id extraction bug where `oauth.fallback.username.prefix` was ignored, and added `oauth.username.prefix`

A bug was introduced in 0.13.0 that resulted in `oauth.fallback.username.prefix` being ignored. This PR fixes that.

A new configuration option is introduced: `oauth.username.prefix`.

This allows for the consistent mapping of user ids into the same name space and may be needed to prevent name collisions.

See [PR 230](https://github.com/strimzi/strimzi-kafka-oauth/pull/230)

### Added support for SASL extension parameters

Adds support for passing SASL extensions via OAuth configuration options, by using a prefix: `oauth.sasl.extension.`

If Kafka Broker uses some other custom `OAUTHBEARER` implementation, it may require SASL extensions options to be sent by the Kafka client.

See [PR 231](https://github.com/strimzi/strimzi-kafka-oauth/pull/231)

0.14.0
------

### Use Kafka 3.6.0

Kafka 3.6.0 has slightly changed how exceptions thrown from plugin extensions are wrapped into KafkaException.
Whereas before these exceptions were set as cause on the received KafkaException, there is now another KafkaException in between.
For example, what was before 3.6.0 a 'KafkaException caused by LoginException' is now (since 3.6.0) 'KafkaException caused by KafkaException caused by LoginException'.

This change of behavior may affect your Kafka client applications, as they may have to change the exception handling logic to act on the final cause in a chain of causes. 

See [PR 205](https://github.com/strimzi/strimzi-kafka-oauth/pull/205)

### Fix logging of principal extraction configuration at startup

See [PR 202](https://github.com/strimzi/strimzi-kafka-oauth/pull/202)

### Support disabling the Accept header when requesting Json Web Key Sets

Some authorization servers have issues with `Accept: application/json` request header. This fix addresses that.

See [PR 201](https://github.com/strimzi/strimzi-kafka-oauth/pull/201)

0.13.0
------

### KeycloakRBACAuthorizer has been superseded by KeycloakAuthorizer and works in both Zookeeper and KRaft mode

While `KeycloakRBACAuthorizer` can still be used in Zookeeper mode, for the future you should migrate your configuration to use `KeycloakAuthorizer`:

In your `server.properties` use:
```
authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer
```

As part of supporting KRaft mode the grants mapping logic has changed slightly. Rather than using the access token as a unit of grant, the user id is now used. 
This results in better sharing of the grants between sessions of the same user, and should also reduce the number of grants held in cache, and the number of refresh requests to the Keycloak server.

Due to these changes additional configuration options have been added:
* `strimzi.authorization.grants.max.idle.time.seconds` specifies the time after which an idle grant in the cache can be garbage collected
* `strimzi.authorization.grants.gc.period.seconds` specifies an interval in which cleaning of stale grants from grants cache is performed

Also, as a result the option `strimzi.authorization.reuse.grants` now defaults to `true`, and no longer to `false`.

See [PR 188](https://github.com/strimzi/strimzi-kafka-oauth/pull/188)

### Option `strimzi.oauth.metric.reporters` added to supersede `metric.reporters` in OAuth metric

Due to integration difficulties of OAuth metrics with Kafka metrics system the OAuth has to instantiate its own copy of metric reporters.
It turns out that some metric reporters don't work correctly when instantiated multiple times. To address that, we no longer use Kafka's `metric.reporters` configuration.

If `strimzi.oauth.metric.reporters` is not set OAuth metrics will still instantiate a default `org.apache.kafka.common.metrics.JmxReporter` if any OAuth metrics are enabled.
In order to install some other metric reporter in addition to `JmxReporter` both have to be listed.
Also, the suggested way to configure it on the Kafka broker is to set it as an env variable, rather than a property in `server.properties` file: 
```
export OAUTH_ENABLE_METRICS=true
export STRIMZI_OAUTH_METRIC_REPORTERS=org.apache.kafka.common.metrics.JmxReporter,org.some.package.SomeReporter
bin/kafka-server-start.sh config/server.properties
```

See [PR 193](https://github.com/strimzi/strimzi-kafka-oauth/pull/193)

### Principal extraction from nested username claim was added

It is now possible to use JsonPath query to target nested attributes when extracting a principal.
For example:
```
oauth.username.claim="['user.info'].['user.id']"
oauth.fallback.username.claim="['user.info'].['client.id']"
```

See [PR 194](https://github.com/strimzi/strimzi-kafka-oauth/pull/194)

### Fixed json-path handling of null

This change introduces a backwards incompatible change in how queries using `equals` or `not equals` comparison to `null` are handled.

Previously the query `"@.missing == null"` where JWT token claim called `missing` was not present in the token would evaluate to `false`.
Similarly the query `"@.missing != null"` would evaluate to `true`.

Such behavior is clearly non-intuitive, and was recognised as a bug and fixed in the [json-path](https://github.com/json-path/jsonpath) library.

By bumping the version of `json-path` to `2.8.0` the behaviour is now fixed. The query `"@.missing == null"` evaluates to `true`, and
`"@.missing != null"` evaluates to `false`.

The documentation in [README.md](README.md#custom-claim-checking) has always contained a note that one should not use `null` comparison in the queries.
Those who followed that rule will not be affected.

0.12.0
------

### Java 17 support

Project can now be compiled and tests performed by Java 8, Java 11, and Java 17.

### Fixed handling of `strimzi.authorization.enable.metrics`

The option was ignored due to a bug.

### Multiple improvements in KeycloakRBACAuthorizer

Some optimization have been done to reduce the number of grants requests to the Keycloak.

A retry mechanism for unexpected failures was added. A configuration option `strimzi.authorization.http.retries` was introduced, that if set to anvalue greater than zero,
results in the initial grants request for the session be immediately repeated upon failure for up to the specified number of times.

### Added support for automatic retries during authentication and token validation

Introduced new configuration options `oauth.http.retries` and `oauth.http.retry.pause.millis` that can be used to enable 
automatically retrying failed requests to the authorization server during authentication (to the `token` endpoint), and 
during token validation (to the `introspection` and `userinfo` endpoints).

0.11.0
------

### Added OAuth metrics support

Added support for OAuth related metrics. It is disabled by default. To enable it set `oauth.enable.metrics` OAuth configuration option to `true`. Use `metrics.reporters`, and other Kafka configuration `metrics.*` options to configure the behaviour of metrics capture and how they are exported.

See [README.md](README.md#configuring-the-metrics) for details.

### Added password grant support

The Resource Owner Password Credentials support was added for interoperability in existing corporate environments where established security policies prevent using `client credentials` to authenticate the client applications. The reason can also be purely technical in that the existing Identity and Access Management solution (IAM) only supports user accounts, even where the 'user' is actually an application service.

See [README.md](README.md#password-grant) for details.

### Added `oauth.jwks.ignore.key.use` config option

Set this option to `true` in order to use all the keys in the JWKS response for token signature validation, regardless of their `use` attribute.
This makes it possible to use authorization servers that don't specify `use` attribute in JWKS keys.

### Added support for unprotected truststores

Truststores with empty password are now supported

0.10.0
------

### Added connect and read timeouts for communication with authorization server

Before, when Kafka client or broker connected to the authorization server during authentication or token validation, there was no connect timeout and no read timeout applied. As a result, if a reverse proxy was in front of the authorization server or a network component glitch prevented normal connectivity, it could happen that the authentication request would stall for a long time.

In order to address this, the default connect timeout and read timeout are now both set to 60 seconds and they are configurable via `oauth.connect.timeout.seconds` and `oauth.read.timeout.seconds`.

### Added groups extraction and exposed groups info via OAuthKafkaPrincipal

Added an authentication time mechanism on the broker where a JsonPath query can be configured to extract a set of groups from a JWT token during authentication. A custom authorizer can then retrieve this information through `OAuthKafkaPrincipal` object available during the `authorize()` call.

### Added access to parsed JWT token

When writing a custom authorizer you may need access to the already parsed JWT token or a map of claims returned by the introspection endpoint. A `getJSON()` method has been added to `BearerTokenWithPayload`.

0.9.0
-----

### KeycloakRBACAuthorizer and OAuthSessionAuthorizer migrated to KIP-504

The authorizers have been migrated to Authorizer API that has been introduced in Kafka 2.4.0. 
As a result the authorizer no longer works in Kafka 2.3.x and earlier versions.

The logging to `.grant` and `.deny` logs now takes into account hints from Kafka about whether the authorization decision for specific action should be logged or not.

### Fixed a parsing bug in KeycloakRBACAuthorizer 

Fixed a bug when parsing a `kafka-cluster` section of a Keycloak authorization services resource pattern.

If Keycloak authorization services grants were targeted using a pattern ending with `*` as in `kafka-cluster:*,Topic:my_topic` or `kafka-cluster:prod_*,Topic:my_topic`, the parsing was invalid and resulted in matching of the grant rule to always fail (authorization was denied).

Using just `Topic:my_topic` would correctly match any cluster, and `kafka-cluster:my-cluster,Topic:my_topic` would match only if `my-cluster` was set as a cluster name.

### Fixed concurrency issue with OAuth over PLAIN

If multiple producers or consumers were used concurrently with the same credentials there was a high likelihood of principal presenting as KafkaPrincipal rather than OAuthKafkaPrincipal after successful authentication. As a result, custom authorizer would not recognise and properly match such a session during authorization check. Depending on the custom authorizer it could result in the delegation of authorization decisions to ACL Authorizer, or it might result in denial of permissions.

### Improved error reporting when using Quarkus native without the https enabled

When preparing an https connection to authorization server the reported error would say that the URL was malformed, and the actual cause was not logged.

### Token type check now also passes if 'token_type: "Bearer"' claim is present in JWT token

By default enabled option `oauth.check.access.token.type` triggeres a token type check which checks that the value of `typ` claim in JWT token is set to `Bearer`. If `typ` claim is not present it now falls back to checking if `token_type` claim with value `Bearer` is present in the access token.

0.8.0
-----

### Support for PEM certificates

PEM certificates can now be used directly without being converted to Java Keystore or PKCS12 formats.
To use PEM certificates, set the `oauth.ssl.truststore.type` option to `PEM` and either specify location of the PEM file in `oauth.ssl.truststore.location` or set the certificates directly in `oauth.ssl.truststore.certificates`.  

### Replaced keycloak-core library with nimbus-jose-jwt

Now JWT token validation uses a different third-party library. As a result ECDSA support no longer requires the BouncyCastle library. Also, some JWT tokens that would fail previously, can now be handled, widening the support of different authorization servers.

### Option `oauth.audience` has been added to client and server configuration

Sometimes authorization server may require `audience` option to be passed when authenticating to the token endpoint.

### Pass the configured `oauth.scope` option on the Kafka broker as `scope` when performing clientId + secret authentication on the broker

While the option has existed, it was only used for inter-broker authentication, but not for `OAuth over PLAIN`. 

0.7.2
-----

### Introduced 'no-client-credentials' mode with OAuth over PLAIN

It is now possible to not specify the `oauth.token.endpoint.uri` parameter when configuring OAuth over PLAIN on the listener, which
results in `username` to always be treated as an account id, and `password` to always be treated as a raw access token without any prefix. In this mode the client can't authenticate using client credentials (Client ID + secret) - even if the client sends them, the server will always interpret it as account id, and an access token, resulting in authentication failing due to `invalid token`.

0.7.1
-----

### Fixed OAuth over PLAIN intermittent failures

The initial implementation of OAuth over PLAIN was based on an invalid assumptions about how threads are assigned to handle messages from different connections in Kafka. An internal assertion often got triggered during concurrent usage, causing authentication failures.

A breaking change with how `$accessToken` is used in 0.7.0 had to be introduced.

Before, you could authenticate with an access token by setting `username` to `$accessToken` and setting the `password` to the access token string.

Now, you have to set `username` to be the same as the principal name that the broker will extract from the given access token (for example, the value of the claim configured by `oauth.username.claim`) and set the `password` to `$accessToken:` followed by the access token string.

So, compared to before, you now prefix the access token string with `$accessToken:` in the `password` parameter, and you have to be careful to properly set the `username` parameter to the principal name matching that in the access token.

### Fixed 'resource' permissions using Keycloak Authorization Services

See [Keycloak authorizer NPE](https://github.com/strimzi/strimzi-kafka-oauth/pull/97).

0.7.0
-----

### OAuth over PLAIN

SASL/PLAIN can now be used to perform the authentication using a service account clientId and secret or a long-lived access token.

When configuring OAuth authentication you should configure the custom principal builder factory:

    principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrinipalBuilder

That is needed by OAuth over PLAIN to function correctly, and is also required by `KeycloakRBACAuthorizer` to function correctly, so it is best to just always configure it.

See [README.md] for instructions on how to set up the brokers and the clients.

### Audience checking

Additional server-side configuration option was added to enable / disable the audience checking during authentication:
* `oauth.check.audience` (e.g. "true")

See [README.md] for more information.


### Custom claim checking

Another token validation mechanism was added which allows using the JSONPath filter queries to express the additional conditions that
the token has to match during authentication in order to pass validation.

To enable it set the following option to a valid JSONPath filter query:
* `oauth.custom.claim.check` (e.g. "'kafka-user' in @.roles")

See [README.md] for more information.


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

Support for token-based authentication that plugs into Kafka's SASL/OAUTHBEARER mechanism to provide:
* Different ways of access token retrieval for Kafka clients (clientId + secret, refresh token, or direct access token)
* Fast signature-checking token validation mechanism (using authorization server's JWKS endpoint)
* Introspection based token validation mechanism (using authorization server's introspection endpoint)

See the [tutorial](examples/README.md).
