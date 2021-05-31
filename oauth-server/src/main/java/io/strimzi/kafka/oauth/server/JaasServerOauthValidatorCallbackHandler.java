/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.IOUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.services.ValidatorKey;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.validator.OAuthIntrospectionValidator;
import io.strimzi.kafka.oauth.validator.TokenValidator;
import io.strimzi.kafka.oauth.validator.TokenValidationException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.TokenIntrospection.debugLogJWT;

/**
 * This <em>CallbackHandler</em> implements the OAuth2 support.
 *
 * It is designed for use with the <em>org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule</em> which provides
 * SASL/OAUTHBEARER authentication support to Kafka brokers. With this CallbackHandler installed, the client authenticates
 * with SASL/OAUTHBEARER mechanism, authenticating with an access token which this <em>CallbackHandler</em> validates, and either
 * accepts or rejects.
 * <p>
 * This <em>CallbackHandler</em> supports two different validation mechanism:
 * </p>
 * <ul>
 *   <li>Fast local signature check by using JWKS certificates to validate the signature on the token</li>
 *   <li>Introspection endpoint based mechanism where token is sent to the authorization server for validation</li>
 * </ul>
 * <p>
 * To install this <em>CallbackHandler</em> in your Kafka listener, specify the following in your 'server.properties':
 * </p>
 * <pre>
 *     # Declare a listener
 *     listeners=CLIENT://kafka:9092
 *
 *     # Specify whether the TCP connection is unsecured or protected with TLS
 *     #listener.security.protocol.map=CLIENT:SASL_PLAINTEXT
 *     listener.security.protocol.map=CLIENT:SASL_SSL
 *
 *     # Enable SASL/OAUTHBEARER authentication mechanism on your listener in addition to any others
 *     #sasl.enabled.mechanisms: PLAIN,OAUTHBEARER
 *     sasl.enabled.mechanisms: OAUTHBEARER
 *
 *     # Install the SASL/OAUTHBEARER LoginModule using per-listener sasl.jaas.config
 *     listener.name.client.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
 *         oauth.valid.issuer.uri="https://java-server" \
 *         oauth.jwks.endpoint.uri="http://java-server/certs" \
 *         oauth.username.claim="preferred_username";
 *
 *     # Install this CallbackHandler to provide custom handling of authentication
 *     listener.name.client.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler
 * </pre>
 * <p>
 * There is additional <em>sasl.jaas.config</em> configuration that may need to be specified in order for this CallbackHandler to work with your authorization server.
 * </p>
 * <blockquote>
 * Note: The following configuration keys can be specified as parameters to <em>sasl.jaas.config</em> in Kafka `server.properties` file, or as
 * ENV vars in which case an all-uppercase key name is also attempted with '.' replaced by '_' (e.g. OAUTH_VALID_ISSUER_URI).
 * They can also be specified as system properties. The priority is in reverse - system property overrides the ENV var, which overrides
 * `server.properties`. When not specified as the parameters to <em>sasl.jaas.config</em>, the configuration keys will apply to all listeners.
 * </blockquote>
 * <p>
 * Configuring the fast local token validation
 * </p><p>
 * Required <em>sasl.jaas.config</em> configuration:
 * </p>
 * <ul>
 * <li><em>oauth.jwks.endpoint.uri</em> A URL of the authorization server endpoint where certificates are available in JWKS format<br>
 * The certificates are used to check JWT token signatures.
 * <li><em>oauth.valid.issuer.uri</em> A URL of the authorization server's token endpoint.<br>
 * The token endpoint is used to authenticate to authorization server with the <em>clientId</em> and the <em>secret</em> received over username and password parameters.
 * </li>
 * </ul>
 * <p>
 * Optional <em>sasl.jaas.config</em> configuration:
 * </p>
 * <ul>
 * <li><em>oauth.jwks.refresh.seconds</em> Configures how often the JWKS certificates are refreshed. <br>
 * The refresh interval has to be at least 60 seconds shorter then the expiry interval specified in <em>oauth.jwks.expiry.seconds</em>. Default value is <em>300</em>.</li>
 * <li><em>oauth.jwks.expiry.seconds</em> Configures how often the JWKS certificates are considered valid. <br>
 * The expiry interval has to be at least 60 seconds longer then the refresh interval specified in <em>oauth.jwks.refresh.seconds</em>. Default value is <em>360</em>.</li>
 * <li><em>oauth.jwks.refresh.min.pause.seconds</em> The minimum pause between two consecutive refreshes. <br>
 * When an unknown signing key is encountered the refresh is scheduled immediately, but will always wait for this minimum pause. Default value is <em>1</em>.</li>
 * </ul>
 * <p>
 * Configuring the introspection endpoint based token validation
 * </p><p>
 * Required <em>sasl.jaas.config</em> configuration:
 * </p>
 * <ul>
 * <li><em>oauth.introspection.endpoint.uri</em> A URL of the token introspection endpoint to which token validation is delegates. <br>
 * It can be used to validate opaque non-JWT tokens which can't be checked using fast local token validation.</li>
 * <li><em>oauth.client.id</em> OAuth client id which the Kafka broker uses to authenticate against the authorization server to use the introspection endpoint.</li>
 * <li><em>oauth.client.secret</em> The OAuth client secret which the Kafka broker uses to authenticate to the authorization server and use the introspection endpoint.</li>
 * </ul>
 * <p>
 * Optional <em>sasl.jaas.config</em> configuration:
 * </p>
 * <ul>
 * <li><em>oauth.userinfo.endpoint.uri</em> A URL of the token introspection endpoint which can be used to validate opaque non-JWT tokens.<br>
 * <li><em>oauth.valid.token.type</em> If set, the token type returned by the introspection endpoint has to match the configured value.<br>
 * </ul>
 * <p>
 * Common optional <em>sasl.jaas.config</em> configuration:
 * <ul>
 * <li><em>oauth.username.claim</em> The attribute key that should be used to extract the user id. If not set `sub` attribute is used.<br>
 * The attribute key refers to the JWT token claim when fast local validation is used, or to attribute in the response by introspection endpoint when introspection based validation is used. It has no default value.</li>
 * <li><em>oauth.fallback.username.claim</em> The fallback username claim to be used for the user id if the attribute key specified by `oauth.username.claim` <br>
 * is not present. This is useful when `client_credentials` authentication only results in the client id being provided in another claim.
 * <li><em>oauth.fallback.username.prefix</em> The prefix to use with the value of <em>oauth.fallback.username.claim</em> to construct the user id.  <br>
 * This only takes effect if <em>oauth.fallback.username.claim</em> is <em>true</em>, and the value is present for the claim.
 * Mapping usernames and client ids into the same user id space is useful in preventing name collisions.</li>
 * <li><em>oauth.check.issuer</em> Enable or disable issuer checking. <br>
 * By default issuer is checked using the value configured by <em>oauth.valid.issuer.uri</em>. Default value is <em>true</em></li>
 * <li><em>oauth.check.audience</em> Enable or disable audience checking. <br>
 * Enabling audience check limits access only to tokens explicitly issued for access to your Kafka broker. If enabled, the <em>oauth.client.id</em> also has to be configured. Default value is <em>false</em></li>
 * <li><em>oauth.access.token.is.jwt</em> Configure whether the access token is treated as JWT. <br>
 * This must be set to <em>false</em> if the authorization server returns opaque tokens. Default value is <em>true</em>.</li>
 * <li><em>oauth.tokens.not.jwt</em> Deprecated. Same as <em>oauth.access.token.is.jwt</em> with opposite meaning.</li>
 * <li><em>oauth.check.access.token.type</em> Configure whether the access token type check is performed or not. <br>
 * This should be set to <em>false</em> if the authorization server does not include <em>typ</em> claim in JWT token. Default value is <em>true</em>.</li>
 * <li><em>oauth.validation.skip.type.check</em> Deprecated. Same as <em>oauth.check.access.token.type</em> with opposite meaning.</li>
 * <li><em>oauth.custom.claim.check</em> The optional mechanism to validate the JWT token or the introspection endpoint response by using any claim or attribute with a JSONPath filter query that evaluates to true or false.
 * If it evaluates to true the check passes, otherwise the token is rejected. See {@link JsonPathFilterQuery}.
 * </ul>
 * <p>
 * TLS <em>sasl.jaas.config</em> configuration for TLS connectivity with the authorization server:
 * </p>
 * <ul>
 * <li><em>oauth.ssl.truststore.location</em> The location of the truststore file on the filesystem.<br>
 * If not present, <em>oauth.ssl.truststore.location</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>oauth.ssl.truststore.password</em> The password for the truststore.<br>
 * If not present, <em>oauth.ssl.truststore.password</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>oauth.ssl.truststore.type</em> The truststore type.<br>
 * If not present, <em>oauth.ssl.truststore.type</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the <a href="https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html#getDefaultType--">Java KeyStore default type</a> is used.
 * </li>
 * <li><em>oauth.ssl.secure.random.implementation</em> The random number generator implementation. See <a href="https://docs.oracle.com/javase/8/docs/api/java/security/SecureRandom.html#getInstance-java.lang.String-">Java SDK documentation</a>.<br>
 * If not present, <em>oauth.ssl.secure.random.implementation</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the Java platform SDK default is used.
 * </li>
 * <li><em>oauth.ssl.endpoint.identification.algorithm</em> Specify how to perform hostname verification. If set to empty string the hostname verification is turned off.<br>
 * If not present, <em>oauth.ssl.endpoint.identification.algorithm</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the default value is <em>HTTPS</em> which enforces hostname verification for server certificates.
 * </li>
 * </ul>
 */
public class JaasServerOauthValidatorCallbackHandler implements AuthenticateCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(JaasServerOauthValidatorCallbackHandler.class);

    private TokenValidator validator;

    private ServerConfig config;

    private boolean isJwt;

    private SSLSocketFactory socketFactory;

    private HostnameVerifier verifier;

    private PrincipalExtractor principalExtractor;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        parseJaasConfig(jaasConfigEntries);

        isJwt = isAccessTokenJwt(config, log, "OAuth validator configuration error: ");

        validateConfig();

        socketFactory = ConfigUtil.createSSLFactory(config);
        verifier = ConfigUtil.createHostnameVerifier(config);

        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String validIssuerUri = config.getValue(ServerConfig.OAUTH_VALID_ISSUER_URI);

        validateIssuerUri(validIssuerUri);

        if (config.getValue("oauth.crypto.provider.bouncycastle") != null) {
            log.warn("The OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE option has been deprecated. ECDSA is automatically available without the need for BouncyCastle JCE provider.");
        }
        if (config.getValue("oauth.crypto.provider.bouncycastle.position") != null) {
            log.warn("The OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE_POSITION option has been deprecated. ECDSA is automatically available without the need for BouncyCastle JCE provider.");
        }

        boolean checkTokenType = isCheckAccessTokenType(config);
        boolean checkAudience = config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_AUDIENCE, false);

        String usernameClaim = config.getValue(Config.OAUTH_USERNAME_CLAIM);
        String fallbackUsernameClaim = config.getValue(Config.OAUTH_FALLBACK_USERNAME_CLAIM);
        String fallbackUsernamePrefix = config.getValue(Config.OAUTH_FALLBACK_USERNAME_PREFIX);

        validateFallbackUsernameParameters(usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix);

        principalExtractor = new PrincipalExtractor(
                usernameClaim,
                fallbackUsernameClaim,
                fallbackUsernamePrefix);

        String clientId = config.getValue(Config.OAUTH_CLIENT_ID);
        String clientSecret = config.getValue(Config.OAUTH_CLIENT_SECRET);

        if (checkAudience && clientId == null) {
            throw new RuntimeException("Oauth validator configuration error: OAUTH_CLIENT_ID must be set when OAUTH_CHECK_AUDIENCE is 'true'");
        }
        String audience = checkAudience ? clientId : null;
        String customClaimCheck = config.getValue(ServerConfig.OAUTH_CUSTOM_CLAIM_CHECK);

        if (!Services.isAvailable()) {
            Services.configure(configs);
        }

        String sslTruststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String sslPassword = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String sslType = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String sslRnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        ValidatorKey vkey;
        Supplier<TokenValidator> factory;

        if (jwksUri != null) {

            int jwksRefreshSeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, 300);
            int jwksExpirySeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, 360);
            int jwksMinPauseSeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_MIN_PAUSE_SECONDS, 1);

            vkey = new ValidatorKey.JwtValidatorKey(
                    validIssuerUri,
                    audience,
                    customClaimCheck,
                    usernameClaim,
                    fallbackUsernameClaim,
                    fallbackUsernamePrefix,
                    sslTruststore,
                    sslPassword,
                    sslType,
                    sslRnd,
                    verifier != null,
                    jwksUri,
                    jwksRefreshSeconds,
                    jwksExpirySeconds,
                    jwksMinPauseSeconds,
                    checkTokenType);

            factory = () -> new JWTSignatureValidator(
                    jwksUri,
                    socketFactory,
                    verifier,
                    principalExtractor,
                    validIssuerUri,
                    jwksRefreshSeconds,
                    jwksMinPauseSeconds,
                    jwksExpirySeconds,
                    checkTokenType,
                    audience,
                    customClaimCheck);

        } else {

            String introspectionEndpoint = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);
            String userInfoEndpoint = config.getValue(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI);
            String validTokenType = config.getValue(ServerConfig.OAUTH_VALID_TOKEN_TYPE);

            vkey = new ValidatorKey.IntrospectionValidatorKey(
                    validIssuerUri,
                    audience,
                    customClaimCheck,
                    usernameClaim,
                    fallbackUsernameClaim,
                    fallbackUsernamePrefix,
                    sslTruststore,
                    sslPassword,
                    sslType,
                    sslRnd,
                    verifier != null,
                    introspectionEndpoint,
                    userInfoEndpoint,
                    validTokenType,
                    clientId,
                    clientSecret);

            factory = () -> new OAuthIntrospectionValidator(
                    introspectionEndpoint,
                    socketFactory,
                    verifier,
                    principalExtractor,
                    validIssuerUri,
                    userInfoEndpoint,
                    validTokenType,
                    clientId,
                    clientSecret,
                    audience,
                    customClaimCheck);
        }

        validator = Services.getInstance().getValidators().get(vkey, factory);
    }

    protected ServerConfig parseJaasConfig(List<AppConfigurationEntry> jaasConfigEntries) {
        if (config != null) {
            return config;
        }
        if (jaasConfigEntries.size() != 1) {
            throw new IllegalArgumentException("Exactly one jaasConfigEntry expected (size: " + jaasConfigEntries.size());
        }

        AppConfigurationEntry e = jaasConfigEntries.get(0);
        Properties p = new Properties();
        p.putAll(e.getOptions());
        config = new ServerConfig(p);
        return config;
    }

    @SuppressWarnings("deprecation")
    private static boolean isCheckAccessTokenType(Config config) {
        String legacy = config.getValue(ServerConfig.OAUTH_VALIDATION_SKIP_TYPE_CHECK);
        if (legacy != null) {
            log.warn("OAUTH_VALIDATION_SKIP_TYPE_CHECK is deprecated. Use OAUTH_CHECK_ACCESS_TOKEN_TYPE (with reverse meaning) instead.");
            if (config.getValue(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE) != null) {
                throw new RuntimeException("OAuth validator configuration error: can't use both OAUTH_CHECK_ACCESS_TOKEN_TYPE and OAUTH_VALIDATION_SKIP_TYPE_CHECK");
            }
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, true);
    }

    private void validateConfig() {
        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String introspectUri = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);

        if ((jwksUri == null) && (introspectUri == null)) {
            throw new RuntimeException("OAuth validator configuration error: either OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) or OAUTH_INTROSPECTION_ENDPOINT_URI (for using authorization server during validation) should be specified!");
        } else if ((jwksUri != null) && (introspectUri != null)) {
            throw new RuntimeException("OAuth validator configuration error: only one of OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) and OAUTH_INTROSPECTION_ENDPOINT_URI (for using authorization server during validation) can be specified!");
        }

        if (jwksUri != null && !isJwt) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) is not compatible with OAUTH_ACCESS_TOKEN_IS_JWT=false");
        }
    }

    private void validateIssuerUri(String validIssuerUri) {
        if (validIssuerUri == null && config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ISSUER, true)) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_VALID_ISSUER_URI must be set or OAUTH_CHECK_ISSUER has to be set to 'false'");
        }
    }

    private void validateFallbackUsernameParameters(String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        if (fallbackUsernameClaim != null && usernameClaim == null) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_USERNAME_CLAIM must be set when OAUTH_FALLBACK_USERNAME_CLAIM is set");
        }

        if (fallbackUsernamePrefix != null && fallbackUsernameClaim == null) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_FALLBACK_USERNAME_CLAIM must be set when OAUTH_FALLBACK_USERNAME_PREFIX is set");
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerValidatorCallback) {
                handleCallback((OAuthBearerValidatorCallback) callback);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    private void handleCallback(OAuthBearerValidatorCallback callback) {
        if (callback.tokenValue() == null) {
            throw new IllegalArgumentException("Callback has null token value!");
        }

        String token = callback.tokenValue();

        debugLogToken(token);

        try {
            TokenInfo ti = validateToken(token);
            callback.token(new BearerTokenWithPayloadImpl(ti));
            if (log.isDebugEnabled()) {
                log.debug("Set validated token on callback: " + callback.token());
            }
        } catch (TokenValidationException e) {
            handleError("Token validation failed for token: " + mask(token), e);
        } catch (RuntimeException e) {
            handleError("Runtime failure during token validation", e);
        } catch (Throwable e) {
            handleError("Unexpected failure during token validation", e);
        }
    }

    private void handleError(String message, Throwable e) {
        handleErrorWithLogger(log, message, e);
    }

    /**
     * Kafka ignores cause inside thrown exception, and doesn't log it.
     * This method makes sure to log the causes, and to prepare the error message for the client.
     * When PLAIN mechanism is used, the error message for the client is generated by <em>PlainSaslServer</em> class,
     * and does not include any of the information in any thrown exception.
     * When OAUTHBEARER is used the error message for the client also includes the message of the thrown exception.
     *
     * @param logger The Logger to use for logging. This allows extending classes to set their own logger.
     * @param message The error message
     * @param e The cause exception
     */
    protected void handleErrorWithLogger(Logger logger, String message, Throwable e) {
        String errId = IOUtil.randomHexString();
        String msg = message + " (ErrId: " + errId + ")";

        if (e instanceof TokenValidationException  || e instanceof SaslAuthenticationException) {
            if (logger.isDebugEnabled()) {
                logger.debug(msg, e);
            }
            message = e.getMessage();
            e = e.getCause() != null ? e.getCause() : e;

        } else if (e instanceof RuntimeException) {
            if (logger.isDebugEnabled()) {
                logger.debug(msg, e);
            }
        } else {
            logger.error(msg, e);
        }

        throw new OAuthSaslAuthenticationException(message, errId, e);
    }

    private TokenInfo validateToken(String token) {
        TokenInfo result = validator.validate(token);
        if (log.isDebugEnabled()) {
            log.debug("User validated (Principal:{})", result == null ? "null" : result.principal());
        }
        return result;
    }

    private void debugLogToken(String token) {
        if (!log.isDebugEnabled() || !isJwt) {
            return;
        }
        debugLogJWT(log, token);
    }

    public boolean isJwt() {
        return isJwt;
    }

    public SSLSocketFactory getSocketFactory() {
        return socketFactory;
    }

    public HostnameVerifier getVerifier() {
        return verifier;
    }

    public PrincipalExtractor getPrincipalExtractor() {
        return principalExtractor;
    }

    static class BearerTokenWithPayloadImpl implements BearerTokenWithPayload {

        private final TokenInfo ti;
        private Object payload;

        BearerTokenWithPayloadImpl(TokenInfo ti) {
            if (ti == null) {
                throw new IllegalArgumentException("TokenInfo == null");
            }
            this.ti = ti;
        }

        @Override
        public Object getPayload() {
            return payload;
        }

        @Override
        public void setPayload(Object value) {
            payload = value;
        }

        @Override
        public String value() {
            return ti.token();
        }

        @Override
        public Set<String> scope() {
            return ti.scope();
        }

        @Override
        public long lifetimeMs() {
            return ti.expiresAtMs();
        }

        @Override
        public String principalName() {
            return ti.principal();
        }

        @Override
        public Long startTimeMs() {
            return ti.issuedAtMs();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BearerTokenWithPayloadImpl that = (BearerTokenWithPayloadImpl) o;
            return Objects.equals(ti, that.ti);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ti);
        }

        @Override
        public String toString() {
            return "BearerTokenWithPayloadImpl (principalName: " + ti.principal() + ", lifetimeMs: " +
                    ti.expiresAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.expiresAtMs()) + " UTC], startTimeMs: " +
                    ti.issuedAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.issuedAtMs()) + " UTC], scope: " + ti.scope() + ")";
        }
    }
}
