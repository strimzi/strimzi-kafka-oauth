/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.IOUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import io.strimzi.kafka.oauth.metrics.IntrospectValidationSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.JwksValidationSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.services.ConfigurationKey;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.TokenIntrospection.debugLogJWT;

/**
 * This <em>CallbackHandler</em> implements the OAuth2 support.
 * <p>
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
 * <li><em>oauth.jwks.ignore.key.use</em> Configure whether any public key in JWKS response should be considered for signature checking or only those explicitly marked as such (some authorization servers require setting this to `true`). Default value is <em>false</em>.</li>
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
 * If it evaluates to true the check passes, otherwise the token is rejected. See {@link JsonPathFilterQuery}.</li>
 * <li><em>oauth.groups.claim</em> The optional mechanism to extract and associate group membership with the account. The query should be specified as a JSONPath that returns a string value or an array of strings.
 * Extracted groups are available on {@link OAuthKafkaPrincipal} object, and can be used by a custom authorizer by invoking the {@link OAuthKafkaPrincipal#getGroups()} method.</li>
 * <li><em>oauth.groups.claim.delimiter</em> When group extraction query returns a string containing multiple groups using a delimiter (comma separated values, for example), you can specify the delimiter to be used. Default value is <em>,</em> (comma)</li>
 * <li><em>oauth.connect.timeout.seconds</em> The maximum time to wait when establishing the connection to the authorization server. Default value is <em>60</em>.</li>
 * <li><em>oauth.read.timeout.seconds</em> The maximum time to wait to read the response from the authorization server after the connection has been established and request sent. Default value is <em>60</em>.</li>
 * <li><em>oauth.fail.fast</em> Configure whether runtime errors at startup should result in Kafka broker shutdown. <br>
 * For example, when configuring fast local token validation the JWKS endpoint is contacted during startup to retrieve the signing keys.
 * If that fails, the Kafka broker shuts down with an error. By setting this to <em>false</em> the Kafka broker will not shutdown, and the validator will keep trying to fetch the JWKS keys. Default value is <em>true</em>.</li>
 * <li><em>oauth.enable.metrics</em> Enable the OAuth metrics. Default value is <em>false</em>.</li>
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

    private int connectTimeout;

    private int readTimeout;

    private int retries;
    private long retryPauseMillis;

    private boolean enableMetrics;

    private OAuthMetrics metrics;
    protected SensorKeyProducer validationSensorKeyProducer;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism)) {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        delegatedConfigure(configs, saslMechanism, jaasConfigEntries);
    }

    /**
     * Part of configuration that can be directly invoked by an overriding class.
     * Parameters are the same as for {@link #configure(Map, String, List)}
     *
     * @param configs Map of config properties
     * @param saslMechanism The mechanism used by the connection
     * @param jaasConfigEntries JAAS config parameters
     */
    public void delegatedConfigure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        parseJaasConfig(jaasConfigEntries);

        isJwt = isAccessTokenJwt(config, log, "OAuth validator configuration error: ");

        validateConfig();

        socketFactory = ConfigUtil.createSSLFactory(config);
        verifier = ConfigUtil.createHostnameVerifier(config);

        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String validIssuerUri = config.getValue(ServerConfig.OAUTH_VALID_ISSUER_URI);

        validateIssuerUri(validIssuerUri);

        checkDeprecatedConfig();

        boolean checkTokenType = isCheckAccessTokenType(config);
        boolean checkAudience = config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_AUDIENCE, false);

        String usernameClaim = config.getValue(Config.OAUTH_USERNAME_CLAIM);
        String fallbackUsernameClaim = config.getValue(Config.OAUTH_FALLBACK_USERNAME_CLAIM);
        String fallbackUsernamePrefix = config.getValue(Config.OAUTH_FALLBACK_USERNAME_PREFIX);

        validateFallbackUsernameParameters(usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix);

        principalExtractor = new PrincipalExtractor(usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix);

        String clientId = config.getValue(Config.OAUTH_CLIENT_ID);
        String clientSecret = config.getValue(Config.OAUTH_CLIENT_SECRET);

        if (checkAudience && clientId == null) {
            throw new ConfigException("OAuth validator configuration error: '" + Config.OAUTH_CLIENT_ID + "' must be set when '"
                    + ServerConfig.OAUTH_CHECK_AUDIENCE + "' is 'true'");
        }
        String audience = checkAudience ? clientId : null;
        String customClaimCheck = config.getValue(ServerConfig.OAUTH_CUSTOM_CLAIM_CHECK);
        String groupQuery = config.getValue(ServerConfig.OAUTH_GROUPS_CLAIM);
        String groupDelimiter = config.getValue(ServerConfig.OAUTH_GROUPS_CLAIM_DELIMITER);

        String sslTruststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String sslPassword = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String sslType = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String sslRnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        connectTimeout = ConfigUtil.getConnectTimeout(config);
        readTimeout = ConfigUtil.getReadTimeout(config);

        configureHttpRetries(config);

        String configId = config.getValue(Config.OAUTH_CONFIG_ID);

        configureMetrics(configs);

        if (jwksUri != null) {
            String effectiveConfigId = setupJWKSValidator(configId, jwksUri, validIssuerUri, checkTokenType,
                    usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix,
                    groupQuery, groupDelimiter, audience, customClaimCheck,
                    sslTruststore, sslPassword, sslType, sslRnd);

            URI jwksEndpointUri = config.getValueAsURI(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
            validationSensorKeyProducer = new JwksValidationSensorKeyProducer(effectiveConfigId, saslMechanism, jwksEndpointUri);
        } else {
            String effectiveConfigId = setupIntrospectionValidator(configId, validIssuerUri, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix,
                    groupQuery, groupDelimiter, clientId, clientSecret, audience, customClaimCheck,
                    sslTruststore, sslPassword, sslType, sslRnd);

            URI introspectionUri = config.getValueAsURI(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);
            validationSensorKeyProducer = new IntrospectValidationSensorKeyProducer(effectiveConfigId, saslMechanism, introspectionUri);
        }
    }

    private void configureMetrics(Map<String, ?> configs) {
        if (!Services.isAvailable()) {
            Services.configure(configs);
        }

        enableMetrics = config.getValueAsBoolean(Config.OAUTH_ENABLE_METRICS, false);
        if (enableMetrics) {
            metrics = Services.getInstance().getMetrics();
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private String setupIntrospectionValidator(String configId, String validIssuerUri, String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix,
                                             String groupQuery, String groupDelimiter, String clientId, String clientSecret, String audience, String customClaimCheck,
                                             String sslTruststore, String sslPassword, String sslType, String sslRnd) {

        String introspectionEndpoint = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);
        String userInfoEndpoint = config.getValue(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI);
        String validTokenType = config.getValue(ServerConfig.OAUTH_VALID_TOKEN_TYPE);

        ValidatorKey vkey = new ValidatorKey.IntrospectionValidatorKey(
                validIssuerUri,
                audience,
                customClaimCheck,
                usernameClaim,
                fallbackUsernameClaim,
                fallbackUsernamePrefix,
                groupQuery,
                groupDelimiter,
                sslTruststore,
                sslPassword,
                sslType,
                sslRnd,
                verifier != null,
                introspectionEndpoint,
                userInfoEndpoint,
                validTokenType,
                clientId,
                clientSecret,
                connectTimeout,
                readTimeout,
                enableMetrics,
                retries,
                retryPauseMillis);

        String effectiveConfigId = configId != null ? configId : vkey.getConfigIdHash();

        Supplier<TokenValidator> factory = () -> new OAuthIntrospectionValidator(
                effectiveConfigId,
                introspectionEndpoint,
                socketFactory,
                verifier,
                principalExtractor,
                groupQuery,
                groupDelimiter,
                validIssuerUri,
                userInfoEndpoint,
                validTokenType,
                clientId,
                clientSecret,
                audience,
                customClaimCheck,
                connectTimeout,
                readTimeout,
                enableMetrics,
                retries,
                retryPauseMillis);

        ConfigurationKey confKey = configId != null ? new ConfigurationKey(configId, vkey) : new ConfigurationKey(vkey.getConfigIdHash(), vkey);
        validator = Services.getInstance().getValidators().get(confKey, factory);

        return effectiveConfigId;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private String setupJWKSValidator(String configId, String jwksUri, String validIssuerUri, boolean checkTokenType,
                                    String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix,
                                    String groupQuery, String groupDelimiter, String audience, String customClaimCheck,
                                    String sslTruststore, String sslPassword, String sslType, String sslRnd) {

        int jwksRefreshSeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, 300);
        int jwksExpirySeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, 360);
        int jwksMinPauseSeconds = config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_MIN_PAUSE_SECONDS, 1);
        boolean failFast = config.getValueAsBoolean(ServerConfig.OAUTH_FAIL_FAST, true);
        boolean jwksIgnoreKeyUse = config.getValueAsBoolean(ServerConfig.OAUTH_JWKS_IGNORE_KEY_USE, false);

        ValidatorKey vkey = new ValidatorKey.JwtValidatorKey(
                validIssuerUri,
                audience,
                customClaimCheck,
                usernameClaim,
                fallbackUsernameClaim,
                fallbackUsernamePrefix,
                groupQuery,
                groupDelimiter,
                sslTruststore,
                sslPassword,
                sslType,
                sslRnd,
                verifier != null,
                jwksUri,
                jwksRefreshSeconds,
                jwksExpirySeconds,
                jwksMinPauseSeconds,
                jwksIgnoreKeyUse,
                checkTokenType,
                connectTimeout,
                readTimeout,
                enableMetrics,
                failFast
        );

        String effectiveConfigId = configId != null ? configId : vkey.getConfigIdHash();

        Supplier<TokenValidator> factory = () -> new JWTSignatureValidator(
                effectiveConfigId,
                jwksUri,
                socketFactory,
                verifier,
                principalExtractor,
                groupQuery,
                groupDelimiter,
                validIssuerUri,
                jwksRefreshSeconds,
                jwksMinPauseSeconds,
                jwksExpirySeconds,
                jwksIgnoreKeyUse,
                checkTokenType,
                audience,
                customClaimCheck,
                connectTimeout,
                readTimeout,
                enableMetrics,
                failFast);

        ConfigurationKey confKey = configId != null ? new ConfigurationKey(configId, vkey) : new ConfigurationKey(vkey.getConfigIdHash(), vkey);
        validator = Services.getInstance().getValidators().get(confKey, factory);

        return effectiveConfigId;
    }

    private void checkDeprecatedConfig() {
        if (config.getValue("oauth.crypto.provider.bouncycastle") != null) {
            log.warn("The 'oauth.crypto.provider.bouncycastle' option has been deprecated. ECDSA is automatically available without the need for BouncyCastle JCE provider.");
        }
        if (config.getValue("oauth.crypto.provider.bouncycastle.position") != null) {
            log.warn("The 'oauth.crypto.provider.bouncycastle.position' option has been deprecated. ECDSA is automatically available without the need for BouncyCastle JCE provider.");
        }
    }

    /**
     * Parse the JAAS config into ServerConfig
     *
     * @param jaasConfigEntries JAAS config parameters
     * @return ServerConfig instance
     */
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
            log.warn("Config option '{}' is deprecated. Use '{}' (with reverse meaning) instead.",
                    ServerConfig.OAUTH_VALIDATION_SKIP_TYPE_CHECK, ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE);
            if (config.getValue(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE) != null) {
                throw new ConfigException("OAuth validator configuration error: can't use both '" + ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE
                        + "' and '" + ServerConfig.OAUTH_VALIDATION_SKIP_TYPE_CHECK + "'");
            }
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, true);
    }

    private void validateConfig() {
        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String introspectUri = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);

        if ((jwksUri == null) && (introspectUri == null)) {
            throw new ConfigException("OAuth validator configuration error: either '" + ServerConfig.OAUTH_JWKS_ENDPOINT_URI
                    + "' (for fast local signature validation) or '" + ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI
                    + "' (for using authorization server during validation) should be specified!");
        } else if ((jwksUri != null) && (introspectUri != null)) {
            throw new ConfigException("OAuth validator configuration error: only one of '" + ServerConfig.OAUTH_JWKS_ENDPOINT_URI
                    + "' (for fast local signature validation) and '" + ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI
                    + "' (for using authorization server during validation) can be specified!");
        }

        if (jwksUri != null && !isJwt) {
            throw new ConfigException("OAuth validator configuration error: '" + ServerConfig.OAUTH_JWKS_ENDPOINT_URI
                    + "' (for fast local signature validation) is not compatible with '" + ServerConfig.OAUTH_ACCESS_TOKEN_IS_JWT + "' set to 'false'");
        }
    }

    private void validateIssuerUri(String validIssuerUri) {
        if (validIssuerUri == null && config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ISSUER, true)) {
            throw new ConfigException("OAuth validator configuration error: '" + ServerConfig.OAUTH_VALID_ISSUER_URI
                    + "' must be set or '" + ServerConfig.OAUTH_CHECK_ISSUER + "' has to be set to 'false'");
        }
    }

    private void validateFallbackUsernameParameters(String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        if (fallbackUsernameClaim != null && usernameClaim == null) {
            throw new ConfigException("OAuth validator configuration error: '" + ServerConfig.OAUTH_USERNAME_CLAIM
                    + "' must be set when '" + ServerConfig.OAUTH_FALLBACK_USERNAME_CLAIM + "' is set");
        }

        if (fallbackUsernamePrefix != null && fallbackUsernameClaim == null) {
            throw new ConfigException("OAuth validator configuration error: '" + ServerConfig.OAUTH_FALLBACK_USERNAME_CLAIM
                    + "' must be set when '" + ServerConfig.OAUTH_FALLBACK_USERNAME_PREFIX + "' is set");
        }
    }

    private void configureHttpRetries(ServerConfig config) {
        retries = config.getValueAsInt(Config.OAUTH_HTTP_RETRIES, 0);
        if (retries < 0) {
            throw new ConfigException("The configured value of 'oauth.http.retries' has to be greater or equal to zero");
        }

        retryPauseMillis = config.getValueAsLong(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, 0);
        if (retries > 0) {
            if (retryPauseMillis < 0) {
                retryPauseMillis = 0;
                log.warn("The configured value of 'oauth.http.retry.pause.millis' is less than zero and will be ignored");
            }
            if (retryPauseMillis <= 0) {
                log.warn("No pause between http retries configured. Consider setting 'oauth.http.retry.pause.millis' to greater than zero to avoid flooding the authorization server with requests.");
            }
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {

        long requestStartTime = System.currentTimeMillis();
        try {
            delegatedHandle(callbacks);

            addValidationMetricSuccessTime(requestStartTime);
        } catch (UnsupportedCallbackException e) {
            // we only support OAuthBearerValidatorCallback, not OAuthBearerExtensionsValidatorCallback
            throw e;
        } catch (Throwable t) {
            addValidationMetricErrorTime(t, requestStartTime);
            throw t;
        }
    }

    /**
     * Handle the callbacks. It can be invoked from a subclass.
     *
     * @param callbacks The callbacks passed to {@link #handle(Callback[])}
     * @throws UnsupportedCallbackException If a callback type is not supported (due to misconfiguration)
     */
    public void delegatedHandle(Callback[] callbacks) throws UnsupportedCallbackException {
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

        try {
            debugLogToken(token);

            TokenInfo ti = validateToken(token);
            callback.token(new BearerTokenWithGrants(ti));
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

        // In case of an Error we don't rethrow it as-is.
        // Doing that would not trigger JVM shutdown, rather the client would
        // automatically keep attempting to authenticate which is counterproductive.

        // We wrap any throwable with an exception type that signals authentication failure
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

    /**
     * Get <code>oauth.access.token.is.jwt</code> configuration
     *
     * @return True if tokens are expected to be JWT tokens
     */
    public boolean isJwt() {
        return isJwt;
    }

    /**
     * Get the <code>SSLSocketFactory</code> if configured.
     * It is configured from <code>oauth.ssl.*</code> config options.
     *
     * @return A configured <code>SSLSocketFactory</code> or null
     */
    public SSLSocketFactory getSocketFactory() {
        return socketFactory;
    }

    /**
     * Get the <code>HostnameVerifier</code> if configured.
     * It is configured from <code>oauth.ssl.endpoint.identification.algorithm</code>
     *
     * @return A configured <code>HostnameVerifier</code>
     */
    public HostnameVerifier getVerifier() {
        return verifier;
    }

    /**
     * Get the configured <code>PrincipalExtractor</code>
     *
     * @return a configured <code>PrincipalExtractor</code>
     */
    public PrincipalExtractor getPrincipalExtractor() {
        return principalExtractor;
    }

    /**
     * Get the configured connect timeout (<code>oauth.connect.timeout.seconds</code>)
     *
     * @return Connect timeout in seconds
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Get the configured read timeout (<code>oauth.read.timeout.seconds</code>)
     *
     * @return Read timeout in seconds
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    protected String getConfigId() {
        if (validator == null) {
            throw new IllegalStateException("This method can only be invoked after the validator was configured");
        }
        return validator.getValidatorId();
    }

    protected int getRetries() {
        return retries;
    }

    protected long getRetryPauseMillis() {
        return retryPauseMillis;
    }

    private void addValidationMetricSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(validationSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addValidationMetricErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(validationSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }
}
