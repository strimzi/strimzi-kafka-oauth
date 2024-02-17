/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.kafka.oauth.client.metrics;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenProvider;
import io.strimzi.kafka.oauth.common.StaticTokenProvider;
import io.strimzi.kafka.oauth.common.FileBasedTokenProvider;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ClientUtils.class);

    /**
     * Configures an access token provider using the provided token and token location.
     *
     * @param token          The access token string.
     * @param tokenLocation  The location of the access token.
     * @return A TokenProvider instance configured with the provided token and location.
     */
    public TokenProvider configureAccessTokenProvider(String token, String tokenLocation) {
        String tokenIgnoredMessage = "Access token location is configured ('" + ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION +
                "'), access token will be ignored ('" + ClientConfig.OAUTH_ACCESS_TOKEN + "').";
        String noSuchFileMessage = "Specified access token location is invalid ('" + ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION + "')";

        return configureAnyTokenProvider(token, tokenLocation, tokenIgnoredMessage, noSuchFileMessage);
    }

    /**
     * Configures a refresh token provider using the provided token and token location.
     *
     * @param token          The refresh token string.
     * @param tokenLocation  The location of the refresh token.
     * @return A TokenProvider instance configured with the provided token and location.
     */
    public TokenProvider configureRefreshTokenProvider(String token, String tokenLocation) {
        String tokenIgnoredMessage = "Refresh token location is configured ('" + ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION +
                "'), refresh token will be ignored ('" + ClientConfig.OAUTH_REFRESH_TOKEN + "').";
        String noSuchFileMessage = "Specified refresh token location is invalid ('" + ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION + "')";

        return configureAnyTokenProvider(token, tokenLocation, tokenIgnoredMessage, noSuchFileMessage);
    }

    /**
     * Configures a client assertion token provider using the provided token and token location.
     *
     * @param token          The client assertion token string.
     * @param tokenLocation  The location of the client assertion token.
     * @return A TokenProvider instance configured with the provided token and location.
     */
    public TokenProvider configureClientAssertionTokenProvider(String token, String tokenLocation) {
        String tokenIgnoredMessage = "Client assertion location is configured ('" + ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION +
                "'), client assertion will be ignored ('" + ClientConfig.OAUTH_CLIENT_ASSERTION + "').";
        String noSuchFileMessage = "Specified client assertion location is invalid ('" + ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION + "')";

        return configureAnyTokenProvider(token, tokenLocation, tokenIgnoredMessage, noSuchFileMessage);
    }

    /**
     * Configures any token provider based on the provided token, token location, and messages.
     *
     * @param token              The token string.
     * @param tokenLocation      The location of the token.
     * @param tokenIgnoredMessage  The message indicating the token is ignored.
     * @param noSuchFileMessage  The message indicating an invalid file location.
     * @return A TokenProvider instance configured accordingly.
     * @throws ConfigException If the specified token location is invalid.
     */
    public TokenProvider configureAnyTokenProvider(String token, String tokenLocation, String tokenIgnoredMessage, String noSuchFileMessage) {
        if (token != null) {
            return new StaticTokenProvider(token);
        } else {
            LOG.warn(tokenIgnoredMessage);
            FileBasedTokenProvider returnValue = null;
            if (tokenLocation != null) {
                try {
                    returnValue = new FileBasedTokenProvider(tokenLocation);
                } catch (IllegalArgumentException e) {
                    throw new ConfigException(noSuchFileMessage + ": " + e.getMessage());
                }
            }
            return returnValue;
        }
    }

    /**
     * Gets the number of HTTP retries configured in the client.
     *
     * @param config  The client configuration.
     * @return The number of HTTP retries.
     * @throws ConfigException If the configured number of retries is less than zero.
     */
    public int getHttpRetries(ClientConfig config) {
        int retries = config.getValueAsInt(Config.OAUTH_HTTP_RETRIES, 0);
        if (retries < 0) {
            throw new ConfigException("The configured value of 'oauth.http.retries' has to be greater or equal to zero");
        }
        return retries;
    }

    /**
     * Gets the pause duration between HTTP retries configured in the client.
     *
     * @param config   The client configuration.
     * @param retries  The number of HTTP retries configured.
     * @return The pause duration between HTTP retries in milliseconds.
     */
    public long getHttpRetryPauseMillis(ClientConfig config, int retries) {
        long retryPauseMillis = config.getValueAsLong(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, 0);
        if (retries > 0) {
            if (retryPauseMillis < 0) {
                retryPauseMillis = 0;
                LOG.warn("The configured value of '{}' is less than zero and will be ignored", Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS);
            }
            if (retryPauseMillis <= 0) {
                LOG.warn("No pause between http retries configured. Consider setting '{}' to greater than zero to avoid flooding the authorization server with requests.", Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS);
            }
        }
        return retryPauseMillis;
    }

    /**
     * Checks the configuration for consistency and potential issues.
     *
     * @param refreshTokenProvider    The refresh token provider.
     * @param username                The username for password grant.
     * @param tokenProvider           The access token provider.
     * @param clientId                The client ID.
     * @param clientAssertionProvider The client assertion token provider.
     * @param password                The password for password grant.
     * @param clientSecret            The client secret.
     * @throws ConfigException If the configuration is invalid or inconsistent.
     */
    public void checkConfiguration(TokenProvider refreshTokenProvider, String username, TokenProvider tokenProvider, String clientId,
                                    TokenProvider clientAssertionProvider, String password, String clientSecret) {
        if (refreshTokenProvider != null) {
            if (username != null) {
                LOG.warn("Refresh token is configured ('{}'), username will be ignored ('{}').",
                        ClientConfig.OAUTH_REFRESH_TOKEN, ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
            }
        }

        if (tokenProvider != null) {
            if (refreshTokenProvider != null) {
                LOG.warn("Access token is configured ('{}'), refresh token will be ignored ('{}', '{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_REFRESH_TOKEN, ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION);
            }
            if (username != null) {
                LOG.warn("Access token is configured ('{}'), username will be ignored ('{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME);
            }
            if (clientId != null) {
                LOG.warn("Access token is configured ('{}'), client id will be ignored ('{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_CLIENT_ID);
            }
            if (clientAssertionProvider != null) {
                LOG.warn("Access token is configured ('{}'), client assertion (location) will be ignored ('{}', '{}').",
                        ClientConfig.OAUTH_ACCESS_TOKEN, ClientConfig.OAUTH_CLIENT_ASSERTION, ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION);
            }
        }  else {
            if (clientId == null) {
                throw new ConfigException("No client id specified ('" + ClientConfig.OAUTH_CLIENT_ID + "')");
            }

            if (username != null && password == null) {
                throw new ConfigException("Username configured ('" + ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME
                        + "') but no password specified ('" + ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD + "')");
            }

            if (refreshTokenProvider == null && clientSecret == null && username == null && clientAssertionProvider == null) {
                throw new ConfigException("No access token or location ('" + ClientConfig.OAUTH_ACCESS_TOKEN
                        + "', '" + ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION + "'), refresh token or location ('"
                        + ClientConfig.OAUTH_REFRESH_TOKEN + "', '" + ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION + "'),"
                        + " client credentials ('" + ClientConfig.OAUTH_CLIENT_SECRET
                        + "'), user credentials ('" + ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME + "')"
                        + " or clientAssertion or location ('" + ClientConfig.OAUTH_CLIENT_ASSERTION + "', '"
                        + ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION + "') specified");
            }

        }
    }
}
