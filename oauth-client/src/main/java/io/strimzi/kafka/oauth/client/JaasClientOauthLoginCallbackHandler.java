package io.strimzi.kafka.oauth.client;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;
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
    private URI tokenEndpoint;
    private String usernameClaim;

    private SSLSocketFactory socketFactory;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        for (AppConfigurationEntry e: jaasConfigEntries) {
            if ("KafkaClient".equals(e.getLoginModuleName()) || e.getLoginModuleName() == null) {
                Properties p = new Properties();
                p.putAll(e.getOptions());
                config = new ClientConfig(p);
            }
        }

        token = config.getValue(ClientConfig.OAUTH_ACCESS_TOKEN);
        if (token == null) {
            String endpoint = config.getValue(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);

            if (endpoint == null) {
                throw new RuntimeException("Access Token not specified ('oauth.access.token'). OAuth2 Token Endpoint ('oauth.token.endpoint.uri') should then be set.");
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
                throw new RuntimeException("No client id specified ('oauth.client.id')");
            }

            if (refreshToken == null && clientSecret == null) {
                throw new RuntimeException("No access token, refresh token, nor client secret specified");
            }

            socketFactory = createSSLFactory();
        }

        usernameClaim = config.getValue(Config.OAUTH_USERNAME_CLAIM, "sub");
        if ("sub".equals(usernameClaim)) {
            usernameClaim = null;
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured JaasClientOauthLoginCallbackHandler:\n    token: " + token
                    + "\n    refreshToken: " + refreshToken + "\n    tokenEndpointUri: " + tokenEndpoint
                    + "\n    clientId: " + clientId + "\n    clientSecret: " + clientSecret
                    + "\n    usernameClaim: " + usernameClaim);
        }
    }

    private SSLSocketFactory createSSLFactory() {
        String truststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String password = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);
        boolean anyHost = config.getValueAsBoolean(Config.OAUTH_SSL_INSECURE_ALLOW_ANY_HOST, false);

        return SSLUtil.createSSLFactory(truststore, password, type, rnd, anyHost);
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
            result = loginWithAccessToken(token);
        } else if (refreshToken != null) {
            result = loginWithRefreshToken(tokenEndpoint, socketFactory, refreshToken, clientId, clientSecret);
        } else if (clientSecret != null) {
            result = loginWithClientSecret(tokenEndpoint, socketFactory, clientId, clientSecret);
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
                return finalResult.expiresAtMs();
            }

            @Override
            public String principalName() {
                return usernameClaim != null ?
                        getClaimFromJWT(usernameClaim, finalResult.payload()) :
                        finalResult.subject();
            }

            @Override
            public Long startTimeMs() {
                return finalResult.issuedAtMs();
            }
        });
    }
}
