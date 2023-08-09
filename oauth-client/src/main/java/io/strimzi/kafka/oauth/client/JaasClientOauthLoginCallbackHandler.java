/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client;

import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.MetricsHandler;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.client.metrics.ClientAuthenticationSensorKeyProducer;
import io.strimzi.kafka.oauth.client.metrics.ClientHttpSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
import io.strimzi.kafka.oauth.services.Services;
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

import static io.strimzi.kafka.oauth.common.Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS;
import static io.strimzi.kafka.oauth.common.ConfigUtil.getConnectTimeout;
import static io.strimzi.kafka.oauth.common.ConfigUtil.getReadTimeout;
import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithAccessToken;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithPassword;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithRefreshToken;

/**
 * A login callback handler class for use with the KafkaProducer, KafkaConsumer, KafkaAdmin clients.
 */
public class JaasClientOauthLoginCallbackHandler implements AuthenticateCallbackHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JaasClientOauthLoginCallbackHandler.class);

    private ClientConfig config = new ClientConfig();

    private String token;
    private String refreshToken;
    private String clientId;
    private String clientSecret;
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

    private boolean enableMetrics;
    private OAuthMetrics metrics;
    private SensorKeyProducer authSensorKeyProducer;
    private SensorKeyProducer tokenSensorKeyProducer;

    private final ClientMetricsHandler authenticatorMetrics = new ClientMetricsHandler();
    private boolean includeAcceptHeader;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException("Unexpected SASL mechanism: " + saslMechanism);
        }

        for (AppConfigurationEntry e: jaasConfigEntries) {
            Properties p = new Properties();
            p.putAll(e.getOptions());
            config = new ClientConfig(p);
        }

        token = config.getValue(ClientConfig.OAUTH_ACCESS_TOKEN);
        if (token == null) {
            String endpoint = config.getValue(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);

            if (endpoint == null) {
                throw new ConfigException("Access token not specified ('" + ClientConfig.OAUTH_ACCESS_TOKEN + "'). OAuth token endpoint ('" + ClientConfig.OAUTH_TOKEN_ENDPOINT_URI + "') should then be set.");
            }

            try {
                tokenEndpoint = new URI(endpoint);
            } catch (URISyntaxException e) {
                throw new ConfigException("Specified token endpoint uri is invalid ('" + ClientConfig.OAUTH_TOKEN_ENDPOINT_URI + "'): " + endpoint, e);
            }
        }

        refreshToken = config.getValue(ClientConfig.OAUTH_REFRESH_TOKEN);

        clientId = config.getValue(Config.OAUTH_CLIENT_ID);
        clientSecret = config.getValue(Config.OAUTH_CLIENT_SECRET);
        username = config.getValue(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
        password = config.getValue(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD);

        scope = config.getValue(Config.OAUTH_SCOPE);
        audience = config.getValue(Config.OAUTH_AUDIENCE);
        socketFactory = ConfigUtil.createSSLFactory(config);
        hostnameVerifier = ConfigUtil.createHostnameVerifier(config);
        connectTimeout = getConnectTimeout(config);
        readTimeout = getReadTimeout(config);
        retries = getHttpRetries(config);
        retryPauseMillis = getHttpRetryPauseMillis(config, retries);
        includeAcceptHeader = config.getValueAsBoolean(Config.OAUTH_INCLUDE_ACCEPT_HEADER, true);
        checkConfiguration();

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

        String configId = configureMetrics(configs);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Configured JaasClientOauthLoginCallbackHandler:"
                    + "\n    configId: " + configId
                    + "\n    token: " + mask(token)
                    + "\n    refreshToken: " + mask(refreshToken)
                    + "\n    tokenEndpointUri: " + tokenEndpoint
                    + "\n    clientId: " + clientId
                    + "\n    clientSecret: " + mask(clientSecret)
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
                    + "\n    enableMetrics: " + enableMetrics
                    + "\n    includeAcceptHeader: " + includeAcceptHeader);
        }
    }

    private int getHttpRetries(ClientConfig config) {
        int retries = config.getValueAsInt(Config.OAUTH_HTTP_RETRIES, 0);
        if (retries < 0) {
            throw new ConfigException("The configured value of 'oauth.http.retries' has to be greater or equal to zero");
        }
        return retries;
    }

    private long getHttpRetryPauseMillis(ClientConfig config, int retries) {
        long retryPauseMillis = config.getValueAsLong(OAUTH_HTTP_RETRY_PAUSE_MILLIS, 0);
        if (retries > 0) {
            if (retryPauseMillis < 0) {
                retryPauseMillis = 0;
                LOG.warn("The configured value of '{}' is less than zero and will be ignored", OAUTH_HTTP_RETRY_PAUSE_MILLIS);
            }
            if (retryPauseMillis <= 0) {
                LOG.warn("No pause between http retries configured. Consider setting '{}' to greater than zero to avoid flooding the authorization server with requests.", OAUTH_HTTP_RETRY_PAUSE_MILLIS);
            }
        }
        return retryPauseMillis;
    }

    private void checkConfiguration() {
        if (token != null) {
            if (refreshToken != null) {
                LOG.warn("Access token is configured ('{}'), refresh token will be ignored ('{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_REFRESH_TOKEN);
            }
            if (username != null) {
                LOG.warn("Access token is configured ('{}'), username will be ignored ('{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
            }
            if (clientId != null) {
                LOG.warn("Access token is configured ('{}'), client id will be ignored ('{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_CLIENT_ID);
            }
        } else if (refreshToken != null) {
            if (username != null) {
                LOG.warn("Refresh token is configured ('{}'), username will be ignored ('{}').",
                        ClientConfig.OAUTH_REFRESH_TOKEN, ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
            }
        }

        if (token == null) {
            if (clientId == null) {
                throw new ConfigException("No client id specified ('" + ClientConfig.OAUTH_CLIENT_ID + "')");
            }

            if (username != null && password == null) {
                throw new ConfigException("Username configured ('" + ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME
                        + "') but no password specified ('" + ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD + "')");
            }

            if (refreshToken == null && clientSecret == null && username == null) {
                throw new ConfigException("No access token ('" + ClientConfig.OAUTH_ACCESS_TOKEN + "'), refresh token ('"
                        + ClientConfig.OAUTH_REFRESH_TOKEN + "'), client credentials ('" + ClientConfig.OAUTH_CLIENT_SECRET
                        + "') or user credentials specified ('" + ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME + "')");
            }
        }
    }

    private String configureMetrics(Map<String, ?> configs) {
        String configId = config.getValue(Config.OAUTH_CONFIG_ID, "client");
        enableMetrics = config.getValueAsBoolean(Config.OAUTH_ENABLE_METRICS, false);

        authSensorKeyProducer = new ClientAuthenticationSensorKeyProducer(configId, tokenEndpoint);
        tokenSensorKeyProducer = tokenEndpoint != null ? new ClientHttpSensorKeyProducer(configId, tokenEndpoint) : null;

        if (!Services.isAvailable()) {
            Services.configure(configs);
        }
        if (enableMetrics) {
            metrics = Services.getInstance().getMetrics();
        }
        return configId;
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
            if (token != null) {
                // we could check if it's a JWT - in that case we could check if it's expired
                result = loginWithAccessToken(token, isJwt, principalExtractor);
            } else if (refreshToken != null) {
                result = loginWithRefreshToken(tokenEndpoint, socketFactory, hostnameVerifier, refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else if (username != null) {
                result = loginWithPassword(tokenEndpoint, socketFactory, hostnameVerifier, username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else if (clientSecret != null) {
                result = loginWithClientSecret(tokenEndpoint, socketFactory, hostnameVerifier, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, authenticatorMetrics, retries, retryPauseMillis, includeAcceptHeader);
            } else {
                throw new IllegalStateException("Invalid oauth client configuration - no credentials");
            }

            addSuccessTime(requestStartTime);
        } catch (Throwable t) {
            addErrorTime(t, requestStartTime);
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

    private void addSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }

    class ClientMetricsHandler implements MetricsHandler {

        @Override
        public void addSuccessRequestTime(long millis) {
            if (enableMetrics) {
                metrics.addTime(tokenSensorKeyProducer.successKey(), millis);
            }
        }

        @Override
        public void addErrorRequestTime(Throwable e, long millis) {
            if (enableMetrics) {
                metrics.addTime(tokenSensorKeyProducer.errorKey(e), millis);
            }
        }
    }
}
