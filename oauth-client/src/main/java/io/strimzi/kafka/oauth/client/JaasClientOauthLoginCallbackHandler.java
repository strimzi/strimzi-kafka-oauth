/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TokenInfo;
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

import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithAccessToken;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithRefreshToken;

public class JaasClientOauthLoginCallbackHandler implements AuthenticateCallbackHandler {

    private static Logger log = LoggerFactory.getLogger(JaasClientOauthLoginCallbackHandler.class);

    private ClientConfig config = new ClientConfig();

    private String token;
    private String refreshToken;
    private String clientId;
    private String clientSecret;
    private String scope;
    private URI tokenEndpoint;

    private boolean isJwt;
    private int maxTokenExpirySeconds;
    private PrincipalExtractor principalExtractor;

    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;

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
                throw new RuntimeException("Access Token not specified (OAUTH_ACCESS_TOKEN). OAuth2 Token Endpoint (OAUTH_TOKEN_ENDPOINT_URI) should then be set.");
            }

            try {
                tokenEndpoint = new URI(endpoint);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Specified token endpoint uri is invalid: " + endpoint);
            }

            refreshToken = config.getValue(ClientConfig.OAUTH_REFRESH_TOKEN);

            clientId = config.getValue(Config.OAUTH_CLIENT_ID);
            clientSecret = config.getValue(Config.OAUTH_CLIENT_SECRET);

            if (clientId == null) {
                throw new RuntimeException("No client id specified (OAUTH_CLIENT_ID)");
            }

            if (refreshToken == null && clientSecret == null) {
                throw new RuntimeException("No access token, refresh token, nor client secret specified");
            }

            scope = config.getValue(Config.OAUTH_SCOPE);
            socketFactory = ConfigUtil.createSSLFactory(config);
            hostnameVerifier = ConfigUtil.createHostnameVerifier(config);
        }

        principalExtractor = new PrincipalExtractor(
                config.getValue(Config.OAUTH_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_PREFIX));

        isJwt = isAccessTokenJwt(config, log, null);
        if (!isJwt && principalExtractor.isConfigured()) {
            log.warn("Token is not JWT (OAUTH_ACCESS_TOKEN_IS_JWT=false) - custom username claim configuration will be ignored (OAUTH_USERNAME_CLAIM, OAUTH_FALLBACK_USERNAME_CLAIM, OAUTH_FALLBACK_USERNAME_PREFIX)");
        }

        maxTokenExpirySeconds = config.getValueAsInt(ClientConfig.OAUTH_MAX_TOKEN_EXPIRY_SECONDS, -1);
        if (maxTokenExpirySeconds > 0 && maxTokenExpirySeconds < 60) {
            throw new RuntimeException("Invalid value configured for OAUTH_MAX_TOKEN_EXPIRY_SECONDS: " + maxTokenExpirySeconds + " (should be at least 60)");
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured JaasClientOauthLoginCallbackHandler:\n    token: " + mask(token)
                    + "\n    refreshToken: " + mask(refreshToken)
                    + "\n    tokenEndpointUri: " + tokenEndpoint
                    + "\n    clientId: " + clientId
                    + "\n    clientSecret: " + mask(clientSecret)
                    + "\n    scope: " + scope
                    + "\n    isJwt: " + isJwt
                    + "\n    maxTokenExpirySeconds: " + maxTokenExpirySeconds
                    + "\n    principalExtractor: " + principalExtractor);
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

        TokenInfo result = null;

        if (token != null) {
            // we could check if it's a JWT - in that case we could check if it's expired
            result = loginWithAccessToken(token, isJwt, principalExtractor);
        } else if (refreshToken != null) {
            result = loginWithRefreshToken(tokenEndpoint, socketFactory, hostnameVerifier, refreshToken, clientId, clientSecret, isJwt, principalExtractor);
        } else if (clientSecret != null) {
            result = loginWithClientSecret(tokenEndpoint, socketFactory, hostnameVerifier, clientId, clientSecret, isJwt, principalExtractor, scope);
        } else {
            throw new IllegalStateException("Invalid oauth client configuration - no credentials");
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
