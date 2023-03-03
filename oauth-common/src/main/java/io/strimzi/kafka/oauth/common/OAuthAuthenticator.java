/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.TokenIntrospection.introspectAccessToken;

@SuppressWarnings("checkstyle:parameternumber")
public class OAuthAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticator.class);

    public static TokenInfo loginWithAccessToken(String token, boolean isJwt, PrincipalExtractor principalExtractor) {
        if (log.isDebugEnabled()) {
            log.debug("loginWithAccessToken() - pass-through access_token: {}", mask(token));
        }

        if (token == null) {
            throw new IllegalArgumentException("No access token specified");
        }

        if (isJwt) {
            // try introspect token
            try {
                return introspectAccessToken(token, principalExtractor);
            } catch (Exception e) {
                log.debug("[IGNORED] Could not parse token as JWT access token. Could not extract scope, subject, and expiry.", e);
            }
        }

        return new TokenInfo(token, "undefined", "undefined", null, System.currentTimeMillis(), System.currentTimeMillis() + 365 * 24 * 3600000L);
    }

    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope) throws IOException {

        return loginWithClientSecret(tokenEndpointUrl, socketFactory, hostnameVerifier,
                clientId, clientSecret, isJwt, principalExtractor, scope, null, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0);
    }

    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience) throws IOException {

        return loginWithClientSecret(tokenEndpointUrl, socketFactory, hostnameVerifier,
                clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("loginWithClientSecret() - tokenEndpointUrl: {}, clientId: {}, clientSecret: {}, scope: {}, audience: {}, connectTimeout: {}, readTimeout: {}, retries: {}, retryPauseMillis: {}",
                    tokenEndpointUrl, clientId, mask(clientSecret), scope, audience, connectTimeout, readTimeout, retries, retryPauseMillis);
        }

        if (clientId == null) {
            throw new IllegalArgumentException("No clientId specified");
        }
        if (clientSecret == null) {
            clientSecret = "";
        }

        String authorization = "Basic " + base64encode(clientId + ':' + clientSecret);

        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        if (scope != null) {
            body.append("&scope=").append(urlencode(scope));
        }
        if (audience != null) {
            body.append("&audience=").append(urlencode(audience));
        }

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis);
    }

    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                              HostnameVerifier hostnameVerifier,
                                              String username, String password,
                                              String clientId, String clientSecret, boolean isJwt,
                                              PrincipalExtractor principalExtractor, String scope, String audience) throws IOException {

        return loginWithPassword(tokenEndpointUrl, socketFactory, hostnameVerifier,
                username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0);
    }


    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                              HostnameVerifier hostnameVerifier,
                                              String username, String password,
                                              String clientId, String clientSecret, boolean isJwt,
                                              PrincipalExtractor principalExtractor, String scope, String audience, int connectTimeout, int readTimeout, int retries, long retryPauseMillis) throws IOException {

        return loginWithPassword(tokenEndpointUrl, socketFactory, hostnameVerifier,
                username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, null, retries, retryPauseMillis);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String username, String password,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis) throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("loginWithPassword() - tokenEndpointUrl: {}, username: {}, password: {}, clientId: {}, clientSecret: {}, scope: {}, audience: {}, connectTimeout: {}, readTimeout: {}, retries: {}, retryPauseMillis: {}",
                    tokenEndpointUrl, username, mask(password), clientId, mask(clientSecret), scope, audience, connectTimeout, readTimeout, retries, retryPauseMillis);
        }

        if (username == null) {
            throw new IllegalArgumentException("No username specified");
        }
        if (clientId == null) {
            throw new IllegalArgumentException("No clientId specified");
        }
        if (clientSecret == null) {
            clientSecret = "";
        }

        String authorization = "Basic " + base64encode(clientId + ':' + clientSecret);

        StringBuilder body = new StringBuilder("grant_type=password");

        body.append("&username=").append(urlencode(username));
        body.append("&password=").append(urlencode(password));

        if (scope != null) {
            body.append("&scope=").append(urlencode(scope));
        }
        if (audience != null) {
            body.append("&audience=").append(urlencode(audience));
        }

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis);
    }

    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope) throws IOException {

        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier,
                refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, null);
    }

    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience) throws IOException {
        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier,
                refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, 0, 0);
    }

    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, int retries, long retryPauseMillis) throws IOException {
        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier, refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, null, retries, retryPauseMillis);
    }

    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("loginWithRefreshToken() - tokenEndpointUrl: {}, refreshToken: {}, clientId: {}, clientSecret: {}, scope: {}, audience: {}, connectTimeout: {}, readTimeout: {}, retries: {}, retryPauseMillis: {}",
                    tokenEndpointUrl, refreshToken, clientId, mask(clientSecret), scope, audience, connectTimeout, readTimeout, retries, retryPauseMillis);
        }

        if (refreshToken == null) {
            throw new IllegalArgumentException("No refresh token specified");
        }

        String authorization = clientSecret != null ?
                "Basic " + base64encode(clientId + ':' + clientSecret) :
                null;

        StringBuilder body = new StringBuilder("grant_type=refresh_token")
                .append("&refresh_token=").append(urlencode(refreshToken))
                .append("&client_id=").append(urlencode(clientId));

        if (scope != null) {
            body.append("&scope=").append(urlencode(scope));
        }
        if (audience != null) {
            body.append("&audience=").append(urlencode(audience));
        }

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis);
    }

    private static TokenInfo post(URI tokenEndpointUri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier,
                                  String authorization, String body, boolean isJwt, PrincipalExtractor principalExtractor,
                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis) throws IOException {

        JsonNode result;
        try {
            result = HttpUtil.doWithRetries(retries, retryPauseMillis, metrics, () ->
                    HttpUtil.post(tokenEndpointUri,
                            socketFactory,
                            hostnameVerifier,
                            authorization,
                            "application/x-www-form-urlencoded",
                            body,
                            JsonNode.class,
                            connectTimeout,
                            readTimeout)
            );

        } catch (Throwable e) {
            Throwable cause = e;
            if (e instanceof ExecutionException) {
                cause = e.getCause();
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IllegalStateException("Unexpected exception while sending HTTP POST request", cause);
        }

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }

        JsonNode expiresIn = result.get("expires_in");
        if (expiresIn == null) {
            throw new IllegalStateException("Invalid response from authorization server: no expires_in");
        }

        // Some OAuth2 authorization servers don't provide scope in this level,
        // therefore we don't need to make it mandatory
        JsonNode scope = result.get("scope");

        if (isJwt) {
            // try introspect token
            try {
                return introspectAccessToken(token.asText(), principalExtractor);
            } catch (Exception e) {
                log.debug("[IGNORED] Could not parse token as JWT access token. Could not extract subject.", e);
            }
        }

        // If token is not a JWT token - we can't introspect it
        long now = System.currentTimeMillis();
        return new TokenInfo(token.asText(), scope != null ? scope.asText() : null, "undefined", null, now, now + expiresIn.asLong() * 1000L);
    }

    public static String base64encode(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static String urlencode(String value) {
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected: Encoding utf-8 not supported");
        }
    }
}
