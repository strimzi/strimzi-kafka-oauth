Release Notes
=============

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
