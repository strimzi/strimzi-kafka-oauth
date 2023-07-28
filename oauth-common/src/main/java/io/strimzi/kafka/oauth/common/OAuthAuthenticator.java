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

/**
 * A class with methods to authenticate a user or a client to the authorization server's token endpoint,
 * and obtain an access token in the form of a <code>TokenInfo</code> object.
 */
@SuppressWarnings("checkstyle:parameternumber")
public class OAuthAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticator.class);

    /**
     * Wrap an access token into TokenInfo extracting information from the token if it is a JWT token.
     * If not a JWT token the principal is set to 'undefined', token creation time to current time, and expiry to 1 year.
     *
     * @param token A raw access token
     * @param isJwt If the access token is a JWT token
     * @param principalExtractor A <code>PrincipalExtractor</code> used to extract the principal (user id)
     * @return A TokenInfo with access token and information extracted from it or set to default values
     */
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

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using client_credentials grant (clientId + secret), and connect and read timeouts of 60 seconds.
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, boolean includeAcceptHeader) throws IOException {

        return loginWithClientSecret(tokenEndpointUrl, socketFactory, hostnameVerifier,
                clientId, clientSecret, isJwt, principalExtractor, scope, null, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using client_credentials grant (clientId + secret), and connect and read timeouts of 60 seconds.
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience An 'audience' attribute to set on the request when authenticating
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience, boolean includeAcceptHeader) throws IOException {

        return loginWithClientSecret(tokenEndpointUrl, socketFactory, hostnameVerifier,
                clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using client_credentials grant (clientId + secret).
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience An 'audience' attribute to set on the request when authenticating
     * @param connectTimeout A connect timeout in seconds
     * @param readTimeout A read timeout in seconds
     * @param metrics A MetricsHandler object to receive metrics collection callbacks
     * @param retries A maximum number of retries if the request fails due to network, or unexpected response status
     * @param retryPauseMillis A pause between consecutive requests
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {
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

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using password grant (username + password), and connect and read timeouts of 60 seconds.
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param username A username
     * @param password A password
     * @param clientId A client id
     * @param clientSecret A (optional) client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience An 'audience' attribute to set on the request when authenticating
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                              HostnameVerifier hostnameVerifier,
                                              String username, String password,
                                              String clientId, String clientSecret, boolean isJwt,
                                              PrincipalExtractor principalExtractor, String scope, String audience, boolean includeAcceptHeader) throws IOException {

        return loginWithPassword(tokenEndpointUrl, socketFactory, hostnameVerifier,
                username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, null, 0, 0, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using password grant (username + password).
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param username A username
     * @param password A password
     * @param clientId A client id
     * @param clientSecret A (optional) client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience An 'audience' attribute to set on the request when authenticating
     * @param connectTimeout A connect timeout in seconds
     * @param readTimeout A read timeout in seconds
     * @param retries A maximum number of retries if the request fails due to network, or unexpected response status
     * @param retryPauseMillis A pause between consecutive requests
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                              HostnameVerifier hostnameVerifier,
                                              String username, String password,
                                              String clientId, String clientSecret, boolean isJwt,
                                              PrincipalExtractor principalExtractor, String scope, String audience, int connectTimeout, int readTimeout, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {

        return loginWithPassword(tokenEndpointUrl, socketFactory, hostnameVerifier,
                username, password, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, null, retries, retryPauseMillis, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using password grant (username + password).
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param username A username
     * @param password A password
     * @param clientId A client id
     * @param clientSecret A (optional) client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience An 'audience' attribute to set on the request when authenticating
     * @param connectTimeout A connect timeout in seconds
     * @param readTimeout A read timeout in seconds
     * @param metrics A MetricsHandler object to receive metrics collection callbacks
     * @param retries A maximum number of retries if the request fails due to network, or unexpected response status
     * @param retryPauseMillis A pause between consecutive requests
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static TokenInfo loginWithPassword(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier,
                                                  String username, String password,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {

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

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using a refresh token, and connect and read timeouts of 60 seconds.
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param refreshToken A refresh token
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, boolean includeAcceptHeader) throws IOException {

        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier,
                refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, null, includeAcceptHeader);
    }

    /**
     * Obtain an access token wrapped into TokenInfo by authenticating to the authorization server's token endpoint
     * using a refresh token, and connect and read timeouts of 60 seconds.
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param refreshToken A refresh token
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience  An 'audience' attribute to set on the request when authenticating
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience, boolean includeAcceptHeader) throws IOException {
        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier,
                refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, audience, HttpUtil.DEFAULT_CONNECT_TIMEOUT, HttpUtil.DEFAULT_READ_TIMEOUT, 0, 0, includeAcceptHeader);
    }

    /**
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param refreshToken A refresh token
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience  An 'audience' attribute to set on the request when authenticating
     * @param connectTimeout A connect timeout in seconds
     * @param readTimeout A read timeout in seconds
     * @param retries A maximum number of retries if the request fails due to network, or unexpected response status
     * @param retryPauseMillis A pause between consecutive requests
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {
        return loginWithRefreshToken(tokenEndpointUrl, socketFactory, hostnameVerifier, refreshToken, clientId, clientSecret, isJwt, principalExtractor, scope, audience, connectTimeout, readTimeout, null, retries, retryPauseMillis, includeAcceptHeader);
    }

    /**
     *
     * @param tokenEndpointUrl A token endpoint url
     * @param socketFactory A socket factory to use with 'https'
     * @param hostnameVerifier A hostname verifier to use with 'https'
     * @param refreshToken A refresh token
     * @param clientId A client id
     * @param clientSecret A client secret
     * @param isJwt If the returned token is expected to be a JWT token
     * @param principalExtractor A PrincipalExtractor to use to determine the principal (user id)
     * @param scope A scope to request when authenticating
     * @param audience  An 'audience' attribute to set on the request when authenticating
     * @param connectTimeout A connect timeout in seconds
     * @param readTimeout A read timeout in seconds
     * @param metrics A MetricsHandler object to receive metrics collection callbacks
     * @param retries A maximum number of retries if the request fails due to network, or unexpected response status
     * @param retryPauseMillis A pause between consecutive requests
     * @param includeAcceptHeader Should we skip sending the Accept header when making outbound http requests
     * @return A TokenInfo with access token and information extracted from it
     * @throws IOException If the request to the authorization server has failed
     * @throws IllegalStateException If the response from the authorization server could not be handled
     */
    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory,
                                                  HostnameVerifier hostnameVerifier, String refreshToken,
                                                  String clientId, String clientSecret, boolean isJwt,
                                                  PrincipalExtractor principalExtractor, String scope, String audience,
                                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("loginWithRefreshToken() - tokenEndpointUrl: {}, refreshToken: {}, clientId: {}, clientSecret: {}, scope: {}, audience: {}, connectTimeout: {}, readTimeout: {}, retries: {}, retryPauseMillis: {}, includeAcceptHeader: {}",
                    tokenEndpointUrl, refreshToken, clientId, mask(clientSecret), scope, audience, connectTimeout, readTimeout, retries, retryPauseMillis, includeAcceptHeader);
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

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString(), isJwt, principalExtractor, connectTimeout, readTimeout, metrics, retries, retryPauseMillis, includeAcceptHeader);
    }

    private static TokenInfo post(URI tokenEndpointUri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier,
                                  String authorization, String body, boolean isJwt, PrincipalExtractor principalExtractor,
                                  int connectTimeout, int readTimeout, MetricsHandler metrics, int retries, long retryPauseMillis, boolean includeAcceptHeader) throws IOException {

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
                            readTimeout,
                            includeAcceptHeader)
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

    /**
     * A helper method to base64 encode a given string
     *
     * @param value A string to encode as base64
     * @return Base64 encoded string
     */
    public static String base64encode(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A helper method to decode a base64 encoded string
     *
     * @param value A string to decode from base64
     * @return A decoded string
     */
    public static String base64decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /**
     * A helper method to urlencode a given value
     *
     * @param value A string to urlencode
     * @return Urlencoded string
     */
    public static String urlencode(String value) {
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected: Encoding utf-8 not supported");
        }
    }
}
