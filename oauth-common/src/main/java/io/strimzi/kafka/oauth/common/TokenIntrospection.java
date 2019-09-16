/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;


public class TokenIntrospection {

    public static TokenInfo introspectAccessToken(String token) {
        JWSInput jws;
        try {
            jws = new JWSInput(token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT token", e);
        }

        try {
            AccessToken parsed = jws.readJsonContent(AccessToken.class);
            return new TokenInfo(parsed, token);

        } catch (Exception e) {
            throw new RuntimeException("Failed to read payload from JWT access token", e);
        }
    }
}
