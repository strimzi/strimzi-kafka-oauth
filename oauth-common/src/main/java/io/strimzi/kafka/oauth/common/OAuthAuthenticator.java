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

import static io.strimzi.kafka.oauth.common.TokenIntrospection.introspectAccessToken;

public class OAuthAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticator.class);

    public static TokenInfo loginWithAccessToken(String token) {
        if (log.isDebugEnabled()) {
            log.debug("loginWithAccessToken() - pass-through access_token: {}", token);
        }
        // try introspect token
        try {
            return introspectAccessToken(token);
        } catch (Exception e) {
            log.info("[IGNORED] Could not parse token as JWT access token. Could not extract scope, subject, and expiry.", e);
        }
        return new TokenInfo(token, "undefined", "undefined", System.currentTimeMillis(), System.currentTimeMillis() + 365 * 24 * 3600000);
    }

    public static TokenInfo loginWithClientSecret(URI tokenEndpointUrl, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String clientId, String clientSecret) throws IOException {
        log.warn("loginWithClientSecret() - tokenEndpointUrl: " + tokenEndpointUrl
                + ", clientId: " + clientId  + ", clientSecret: " + clientSecret);

        String authorization = "Basic " + base64encode(clientId + ':' + clientSecret);

        StringBuilder body = new StringBuilder("grant_type=client_credentials");

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString());
    }

    public static TokenInfo loginWithRefreshToken(URI tokenEndpointUrl, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String refreshToken, String clientId, String clientSecret) throws IOException {
        log.warn("loginWithRefreshToken() - tokenEndpointUrl: " + tokenEndpointUrl + ", refreshToken: " + refreshToken
                + ", clientId: " + clientId + ", clientSecret: " + clientSecret);


        String authorization = clientSecret != null ?
                "Basic " + base64encode(clientId + ':' + clientSecret) :
                null;

        StringBuilder body = new StringBuilder("grant_type=refresh_token")
                .append("&refresh_token=").append(refreshToken)
                .append("&client_id=").append((urlencode(clientId)));

        return post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization, body.toString());
    }

    private static TokenInfo post(URI tokenEndpointUri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, String body) throws IOException {

        long now = System.currentTimeMillis();

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                socketFactory,
                hostnameVerifier,
                authorization,
                "application/x-www-form-urlencoded",
                body,
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }

        JsonNode expiresIn = result.get("expires_in");
        if (expiresIn == null) {
            throw new IllegalStateException("Invalid response from authorization server: no expires_in");
        }

        JsonNode scope = result.get("scope");
        if (scope == null) {
            throw new IllegalStateException("Invalid response from authorization server: no scope");
        }

        // try introspect token
        try {
            return introspectAccessToken(token.asText());
        } catch (Exception e) {
            log.info("[IGNORED] Could not parse token as JWT access token. Could not extract subject.", e);
        }

        return new TokenInfo(token.asText(), scope.asText(), "undefined", now, now + expiresIn.asInt() * 1000);
    }

    public static String base64encode(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String urlencode(String value) {
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected: Encoding utf-8 not supported");
        }
    }
}
