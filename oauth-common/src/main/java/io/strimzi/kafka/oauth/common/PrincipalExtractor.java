/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;

public class PrincipalExtractor {

    private String usernameClaim;
    private String fallbackUsernameClaim;
    private String fallbackUsernamePrefix;

    public PrincipalExtractor() {}

    public PrincipalExtractor(String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        this.usernameClaim = usernameClaim;
        this.fallbackUsernameClaim = fallbackUsernameClaim;
        this.fallbackUsernamePrefix = fallbackUsernamePrefix;
    }

    public String getPrincipal(AccessToken token, JWSInput jws) {
        if (usernameClaim != null) {
            try {
                return getPrincipal(jws.readJsonContent(JsonNode.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse access token", e);
            }
        }
        return token.getSubject();
    }

    public String getPrincipal(JsonNode json) {
        String result;

        if (usernameClaim != null) {
            result = getClaimFromJWT(json, usernameClaim);
            if (result != null) {
                return result;
            }

            if (fallbackUsernameClaim != null) {
                result = getClaimFromJWT(json, fallbackUsernameClaim);
                if (result != null) {
                    return fallbackUsernamePrefix == null ? result : fallbackUsernamePrefix + result;
                }
            }
        }

        return getClaimFromJWT(json, "sub");
    }

    @Override
    public String toString() {
        return "PrincipalExtractor {usernameClaim: " + usernameClaim  + ", fallbackUsernameClaim: " + fallbackUsernameClaim + ", fallbackUsernamePrefix: " + fallbackUsernamePrefix + "}";
    }

    public boolean isConfigured() {
        return usernameClaim != null || fallbackUsernameClaim != null || fallbackUsernamePrefix != null;
    }
}
