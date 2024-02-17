/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client;

import io.strimzi.kafka.oauth.client.metrics.ClientMetricsHandler;
import io.strimzi.kafka.oauth.client.metrics.ClientUtils;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenProvider;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static io.strimzi.kafka.oauth.common.ConfigUtil.getConnectTimeout;
import static io.strimzi.kafka.oauth.common.ConfigUtil.getReadTimeout;
import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithAccessToken;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientAssertion;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithPassword;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithRefreshToken;

/**
 * A login callback handler class for use with the KafkaProducer, KafkaConsumer, KafkaAdmin clients.
 */
public class JaasClientOauthLoginCallbackHandler implements AuthenticateCallbackHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JaasClientOauthLoginCallbackHandler.class);

    private String clientId;
    private String clientSecret;
    private String clientAssertionType;
    private TokenProvider tokenProvider;
    private TokenProvider refreshTokenProvider;
    private TokenProvider clientAssertionProvider;
    private String username;
    private String password;
    private String scope;
    private String audience;
    private URI tokenEndpoint;
    private boolean isJwt;
    private int maxTokenExpirySeconds;
    private PrincipalExtractor principalExtractor;
    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;
    private int connectTimeout;
    private int readTimeout;
    private int retries;
    private long retryPauseMillis;
    private boolean includeAcceptHeader;
    private final ClientMetricsHandler authenticatorMetrics;
    private final ClientUtils clientUtils;

    /**
     * Constructs a new JaasClientOauthLoginCallbackHandler.
     * Initializes the necessary utilities and metrics handler.
     */
    public JaasClientOauthLoginCallbackHandler() {
        this.clientUtils = new ClientUtils();
        this.authenticatorMetrics = new ClientMetricsHandler();
    }

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException("Unexpected SASL mechanism: " + saslMechanism);
        }

        AppConfigurationEntry entry = jaasConfigEntries.get(0);
        Properties p = new Properties();
        p.putAll(entry.getOptions());
        ClientConfig config = new ClientConfig(p);

        final String token = config.getValue(ClientConfig.OAUTH_ACCESS_TOKEN);
        final String tokenLocation = config.getValue(ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION);
        tokenProvider = clientUtils.configureAccessTokenProvider(token, tokenLocation);

        if (token == null && tokenLocation == null) {
            String endpoint = config.getValue(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);

            if (endpoint == null) {
                throw new ConfigException("Access token not specified ('" + ClientConfig.OAUTH_ACCESS_TOKEN + "' or '"
                        + ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION + "'). OAuth token endpoint ('" + ClientConfig.OAUTH_TOKEN_ENDPOINT_URI + "') should then be set.");
            }

            try {
                tokenEndpoint = new URI(endpoint);
            } catch (URISyntaxException e) {
                throw new ConfigException("Specified token endpoint uri is invalid ('" + ClientConfig.OAUTH_TOKEN_ENDPOINT_URI + "'): " + endpoint, e);
            }
        }


        final String refreshToken = config.getValue(ClientConfig.OAUTH_REFRESH_TOKEN);
        final String refreshTokenLocation = config.getValue(ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION);
        refreshTokenProvider = clientUtils.configureRefreshTokenProvider(refreshToken, refreshTokenLocation);

        clientId = config.getValue(Config.OAUTH_CLIENT_ID);
        clientSecret = config.getValue(Config.OAUTH_CLIENT_SECRET);

        final String clientAssertion = config.getValue(ClientConfig.OAUTH_CLIENT_ASSERTION);
        final String clientAssertionLocation = config.getValue(ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION);
        clientAssertionProvider = clientUtils.configureClientAssertionTokenProvider(clientAssertion, clientAssertionLocation);

        clientAssertionType = config.getValue(ClientConfig.OAUTH_CLIENT_ASSERTION_TYPE, "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");

        username = config.getValue(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
        password = config.getValue(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD);

        scope = config.getValue(Config.OAUTH_SCOPE);
        audience = config.getValue(Config.OAUTH_AUDIENCE);
        socketFactory = ConfigUtil.createSSLFactory(config);
        hostnameVerifier = ConfigUtil.createHostnameVerifier(config);
        connectTimeout = getConnectTimeout(config);
        readTimeout = getReadTimeout(config);
        retries = clientUtils.getHttpRetries(config);
        retryPauseMillis = clientUtils.getHttpRetryPauseMillis(config, retries);
        includeAcceptHeader = config.getValueAsBoolean(Config.OAUTH_INCLUDE_ACCEPT_HEADER, true);

        clientUtils.checkConfiguration(refreshTokenProvider, username, tokenProvider, clientId, clientAssertionProvider, password, clientSecret);

        principalExtractor = new PrincipalExtractor(
                config.getValue(Config.OAUTH_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_PREFIX));

        isJwt = isAccessTokenJwt(config, LOG, null);
        if (!isJwt && principalExtractor.isConfigured()) {
            LOG.warn("Token is not JWT ('{}' is 'false') - custom username claim configuration will be ignored ('{}', '{}', '{}')",
                    Config.OAUTH_ACCESS_TOKEN_IS_JWT, ClientConfig.OAUTH_USERNAME_CLAIM, ClientConfig.OAUTH_FALLBACK_USERNAME_CLAIM, ClientConfig.OAUTH_FALLBACK_USERNAME_PREFIX);
        }

        maxTokenExpirySeconds = config.getValueAsInt(ClientConfig.OAUTH_MAX_TOKEN_EXPIRY_SECONDS, -1);
        if (maxTokenExpirySeconds > 0 && maxTokenExpirySeconds < 60) {
            throw new ConfigException("Invalid value configured for '" + ClientConfig.OAUTH_MAX_TOKEN_EXPIRY_SECONDS + "': " + maxTokenExpirySeconds + " (should be at least 60)");
        }

        String configId = authenticatorMetrics.configureMetrics(configs, config, this.tokenEndpoint);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Configured JaasClientOauthLoginCallbackHandler:"
                    + "\n    configId: " + configId
                    + "\n    token: " + mask(token)
                    + "\n    tokenLocation: " + tokenLocation
                    + "\n    refreshToken: " + mask(refreshToken)
                    + "\n    refreshTokenLocation: " + refreshTokenLocation
                    + "\n    tokenEndpointUri: " + tokenEndpoint
                    + "\n    clientId: " + clientId
                    + "\n    clientSecret: " + mask(clientSecret)
                    + "\n    clientAssertion: " + mask(clientAssertion)
                    + "\n    clientAssertionLocation: " + clientAssertionLocation
                    + "\n    clientAssertionType: " + clientAssertionType
                    + "\n    username: " + username
                    + "\n    password: " + mask(password)
                    + "\n    scope: " + scope
                    + "\n    audience: " + audience
                    + "\n    isJwt: " + isJwt
                    + "\n    maxTokenExpirySeconds: " + maxTokenExpirySeconds
                    + "\n    principalExtractor: " + principalExtractor
                    + "\n    connectTimeout: " + connectTimeout
                    + "\n    readTimeout: " + readTimeout
                    + "\n    retries: " + retries
                    + "\n    retryPauseMillis: " + retryPauseMillis
                    + "\n    enableMetrics: " + authenticatorMetrics.isEnableMetrics()
                    + "\n    includeAcceptHeader: " + includeAcceptHeader);
        }
    }


    @Override
    public void close() {

    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerTokenCallback) {
                handleCallback((OAuthBearerTokenCallback) callback);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    private void handleCallback(OAuthBearerTokenCallback callback) throws IOException {
        if (callback.token() != null) {
            throw new IllegalArgumentException("Callback had a token already");
        }

        TokenInfo result;

        long requestStartTime = System.currentTimeMillis();
        try {
            if (tokenProvider != null) {
                // we could check if it's a JWT - in that case we could check if it's expired
                result = loginWithAccessToken(tokenProvider.token(), isJwt, principalExtractor);
            } else if (refreshTokenProvider != null) {
                result = loginWithRefreshToken(tokenEndpoint, socketFactory, hostnameVerifier, refreshTokenProvider.token(), clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else if (username != null) {
                result = loginWithPassword(tokenEndpoint, socketFactory, hostnameVerifier, username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else if (clientSecret != null) {
                result = loginWithClientSecret(tokenEndpoint, socketFactory, hostnameVerifier, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else if (clientAssertionProvider != null) {
                result = loginWithClientAssertion(tokenEndpoint, socketFactory, hostnameVerifier, clientId, clientAssertionProvider.token(), clientAssertionType, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else {
                throw new IllegalStateException("Invalid oauth client configuration - no credentials");
            }

            authenticatorMetrics.addSuccessTime(requestStartTime);
        } catch (Throwable t) {
            authenticatorMetrics.addErrorTime(t, requestStartTime);
            throw t;
        }

        TokenInfo finalResult = result;

        callback.token(new OAuthBearerToken() {
            @Override
            public String value() {
                return finalResult.token();
            }

            @Override
            public Set<String> scope() {
                return finalResult.scope();
            }

            @Override
            public long lifetimeMs() {
                long maxExpiresAt = finalResult.issuedAtMs() + maxTokenExpirySeconds * 1000L;
                if (maxTokenExpirySeconds > 0 && finalResult.expiresAtMs() > maxExpiresAt) {
                    return maxExpiresAt;
                }
                return finalResult.expiresAtMs();
            }

            @Override
            public String principalName() {
                return finalResult.principal();
            }

            @Override
            public Long startTimeMs() {
                return finalResult.issuedAtMs();
            }
        });
    }



}
