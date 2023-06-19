/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;

/**
 * An object with logic for extracting a principal name (i.e. a user id) from a JWT token.
 * <p>
 * First a claim configured as <code>usernameClaim</code> is looked up.
 * If not found the claim configured as <code>fallbackUsernameClaim</code> is looked up. If that one is found and if
 * the <code>fallbackUsernamePrefix</code> is configured prefix the found value with the prefix, otherwise not.
 */
public class PrincipalExtractor {

    private String usernameClaim;
    private String fallbackUsernameClaim;
    private String fallbackUsernamePrefix;

    /**
     * Create a new instance
     */
    public PrincipalExtractor() {}

    /**
     * Create a new instance
     *
     * @param usernameClaim Attribute name for an attribute containing the user id to lookup first. Use '.' to refer to nested child objects.
     * @param fallbackUsernameClaim Attribute name for an attribute containg the user id to lookup as a fallback
     * @param fallbackUsernamePrefix A prefix to prepend to the value of the fallback attribute value if set
     */
    public PrincipalExtractor(String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        this.usernameClaim = usernameClaim;
        this.fallbackUsernameClaim = fallbackUsernameClaim;
        this.fallbackUsernamePrefix = fallbackUsernamePrefix;
    }

    /**
     * Get the principal name
     *
     * @param json JWT token as a <code>JsonNode</code> object
     * @return Principal name
     */
    public String getPrincipal(JsonNode json) {
        String result;

        if (usernameClaim != null) {
            result = getClaimFromJWT(usernameClaim, json);
            if (result != null) {
                return result;
            }

            if (fallbackUsernameClaim != null) {
                result = getClaimFromJWT(fallbackUsernameClaim, json);
                if (result != null) {
                    return fallbackUsernamePrefix == null ? result : fallbackUsernamePrefix + result;
                }
            }
        }

        return null;
    }

    /**
     * Get the value of <code>sub</code> claim
     *
     * @param json JWT token as a <code>JsonNode</code> object
     * @return The value of <code>sub</code> attribute
     */
    public String getSub(JsonNode json) {
        return getClaimFromJWT(json, "sub");
    }

    @Override
    public String toString() {
        return "PrincipalExtractor {usernameClaim: " + usernameClaim  + ", fallbackUsernameClaim: " + fallbackUsernameClaim + ", fallbackUsernamePrefix: " + fallbackUsernamePrefix + "}";
    }

    /**
     * Return true if any of the configuration options is configured
     *
     * @return True if any of the constructor parameters is set
     */
    public boolean isConfigured() {
        return usernameClaim != null || fallbackUsernameClaim != null || fallbackUsernamePrefix != null;
    }
}
