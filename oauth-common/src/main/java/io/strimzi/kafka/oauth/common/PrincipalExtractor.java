/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;

/**
 * An object with logic for extracting a principal name (i.e. a user id) from a JWT token.
 * <p>
 * First a claim configured as <code>usernameClaim</code> is looked up.
 * If not found the claim configured as <code>fallbackUsernameClaim</code> is looked up. If that one is found and if
 * the <code>fallbackUsernamePrefix</code> is configured prefix the found value with the prefix, otherwise not.
 * <p>
 * Claims configuration can refer to a nested attribute as well.
 * Examples of configurations:
 * <pre>
 *     userId              ... use top level attribute named 'userId'
 *     user.id             ... use top level attribute named 'user.id'
 *     [userInfo].[id]     ... use nested attribute 'id' under 'userInfo' top level attribute
 *     ['userInfo'].['id'] ... use nested attribute 'id' under 'userInfo' top level attribute
 * </pre>
 */
public class PrincipalExtractor {

    private final List<String> usernameClaim;
    private final List<String> fallbackUsernameClaim;
    private final String fallbackUsernamePrefix;

    /**
     * Create a new instance
     */
    public PrincipalExtractor() {
        usernameClaim = null;
        fallbackUsernameClaim = null;
        fallbackUsernamePrefix = null;
    }

    /**
     * Create a new instance
     *
     * @param usernameClaim Attribute name for an attribute containing the user id to lookup first.
     * @param fallbackUsernameClaim Attribute name for an attribute containg the user id to lookup as a fallback
     * @param fallbackUsernamePrefix A prefix to prepend to the value of the fallback attribute value if set
     */
    public PrincipalExtractor(String usernameClaim, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        this.usernameClaim = parseClaimSpec(usernameClaim);
        this.fallbackUsernameClaim = parseClaimSpec(fallbackUsernameClaim);
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
            result = getClaimFromJWT(json, usernameClaim.toArray(new String[0]));
            if (result != null) {
                return result;
            }

            if (fallbackUsernameClaim != null) {
                result = getClaimFromJWT(json, fallbackUsernameClaim.toArray(new String[0]));
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

    private static List<String> parseClaimSpec(String spec) {
        spec = spec == null ? null : spec.trim();
        if (spec == null || "".equals(spec)) {
            return null;
        }

        if (!spec.startsWith("[")) {
            return Collections.singletonList(spec);
        }

        LinkedList<String> parsed = new LinkedList<>();
        int pos = 0;
        int epos = 0;
        while (pos != -1) {
            pos += 1;
            epos = spec.indexOf("]", pos);
            if (epos == -1) {
                throw new IllegalArgumentException("Failed to parse username claim spec: '" + spec + "' (Missing ']')");
            }
            parsed.add(removeQuotesAndSpaces(spec.substring(pos, epos)));
            pos = epos + 1;
            epos = skipSpaces(spec, pos);
            if (epos >= spec.length()) {
                return parsed;
            }
            if (spec.charAt(epos) != '.') {
                throw new IllegalArgumentException("Failed to parse usename claim spec: '" + spec + "' (Missing '.' at position: " + epos + ")");
            }
            pos = spec.indexOf("[", epos + 1);
        }

        throw new IllegalArgumentException("Failed to parse username claim spec: '" + spec + "' (Missing '[' at position:" + (epos + 1) + ")");
    }

    private static int skipSpaces(String spec, int pos) {
        int i = pos;
        while (i < spec.length() && spec.charAt(i) == ' ') {
            i += 1;
        }
        return i;
    }

    private static String removeQuotesAndSpaces(String value) {
        value = value.trim();
        if (value.startsWith("'")) {
            if (value.length() == 0) {
                throw new IllegalArgumentException("Failed to parse username claim spec: empty []");
            }
            if (value.length() == 1 || !value.endsWith("'")) {
                throw new IllegalArgumentException("Failed to parse username claim spec: missing ending quote [']: " + value);
            }
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
