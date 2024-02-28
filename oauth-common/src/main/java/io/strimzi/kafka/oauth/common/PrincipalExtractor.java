/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.jsonpath.JsonPathQuery;

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;

/**
 * An object with logic for extracting a principal name (i.e. a user id) from a JWT token.
 * <p>
 * First a claim configured as <code>usernameClaim</code> is looked up.
 * If found, and the <code>usernamePrefix</code> is configured, it is prepended to the value of the claim.
 * If not found, the claim configured as <code>fallbackUsernameClaim</code> is looked up. If that one is found and if
 * the <code>fallbackUsernamePrefix</code> is configured prefix the found value with the prefix, otherwise not.
 * <p>
 * The claim specification uses the following rules:
 * <ul>
 * <li>If the claim specification starts with an opening square bracket '[', it is interpreted as a JsonPath query, and allows
 * targeting a nested attribute. </li>
 * <li>Otherwise, it is interpreted as a top level attribute name.</li>
 * </ul>
 * <p>
 * A JsonPath query is resolved relative to JSON object containing info to identify user
 * (a JWT payload, a response from Introspection Endpoint or a response from User Info Endpoint).
 * <p>
 * For more on JsonPath syntax see https://github.com/json-path/JsonPath.
 * <p>
 * Examples of claim specification:
 * <pre>
 *     userId                    ... use top level attribute named 'userId'
 *     user.id                   ... use top level attribute named 'user.id'
 *     $userid                   ... use top level attribute named '$userid'
 *     ['userInfo']['id']        ... use nested attribute 'id' under 'userInfo' top level attribute
 *     ['userInfo'].id           ... use nested attribute 'id' under 'userInfo' top level attribute (second segment not using brackets)
 *     ['user.info']['user.id']  ... use nested attribute 'user.id' under 'user.info' top level attribute
 *     ['user.info'].['user.id'] ... use nested attribute 'user.id' under 'user.info' top level attribute (optional dot)
 * </pre>
 *
 * See PrincipalExtractorTest.java for more working and non-working examples of claim specification.
 */
public class PrincipalExtractor {

    private final Extractor usernameExtractor;
    private final String usernamePrefix;
    private final Extractor fallbackUsernameExtractor;
    private final String fallbackUsernamePrefix;

    /**
     * Create a new instance
     */
    public PrincipalExtractor() {
        usernameExtractor = null;
        usernamePrefix = null;
        fallbackUsernameExtractor = null;
        fallbackUsernamePrefix = null;
    }

    /**
     * Create a new instance
     *
     * @param usernameClaim Attribute name for an attribute containing the user id to lookup first.
     */
    public PrincipalExtractor(String usernameClaim) {
        this.usernameExtractor = parseClaimSpec(usernameClaim);
        usernamePrefix = null;
        fallbackUsernameExtractor = null;
        fallbackUsernamePrefix = null;
    }

    /**
     * Create a new instance
     *
     * @param usernameClaim Attribute name for an attribute containing the user id to lookup first.
     * @param usernamePrefix A prefix to prepend to the user id
     * @param fallbackUsernameClaim Attribute name for an attribute containg the user id to lookup as a fallback
     * @param fallbackUsernamePrefix A prefix to prepend to the value of the fallback attribute value if set
     */
    public PrincipalExtractor(String usernameClaim, String usernamePrefix, String fallbackUsernameClaim, String fallbackUsernamePrefix) {
        this.usernameExtractor = parseClaimSpec(usernameClaim);
        this.usernamePrefix = usernamePrefix;
        this.fallbackUsernameExtractor = parseClaimSpec(fallbackUsernameClaim);
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

        if (usernameExtractor != null) {
            result = extractUsername(usernameExtractor, json);
            if (result != null) {
                return usernamePrefix != null ? usernamePrefix + result : result;
            }
            if (fallbackUsernameExtractor != null) {
                result = extractUsername(fallbackUsernameExtractor, json);
                return result != null && fallbackUsernamePrefix != null ? fallbackUsernamePrefix + result : result;
            }
        }

        return null;
    }

    private String extractUsername(Extractor extractor, JsonNode json) {
        if (extractor.getAttributeName() != null) {
            String result = getClaimFromJWT(json, extractor.getAttributeName());
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } else {
            JsonNode queryResult = extractor.getJSONPathQuery().apply(json);
            String result = queryResult == null ? null : queryResult.asText().trim();
            if (result != null && !result.isEmpty()) {
                return result;
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
        return "PrincipalExtractor {usernameClaim: " + usernameExtractor + ", usernamePrefix: " + usernamePrefix + ", fallbackUsernameClaim: " + fallbackUsernameExtractor + ", fallbackUsernamePrefix: " + fallbackUsernamePrefix + "}";
    }

    /**
     * Return true if any of the configuration options is configured
     *
     * @return True if any of the constructor parameters is set
     */
    public boolean isConfigured() {
        return usernameExtractor != null || usernamePrefix != null || fallbackUsernameExtractor != null || fallbackUsernamePrefix != null;
    }

    /**
     * The claim specification uses the following rules:
     * <ul>
     * <li>If the claim specification starts with an opening square bracket '[', it is interpreted as a JsonPath query, and allows
     * targeting a nested attribute. </li>
     * <li>Otherwise, it is interpreted as a top level attribute name.</li>
     * </ul>
     * For more on JsonPath syntax see https://github.com/json-path/JsonPath.
     * <p>
     * Examples of claim specification:
     * <pre>
     *     userId                    ... use top level attribute named 'userId'
     *     user.id                   ... use top level attribute named 'user.id'
     *     $userid                   ... use top level attribute named '$userid'
     *     ['userInfo']['id']        ... use nested attribute 'id' under 'userInfo' top level attribute
     *     ['userInfo'].id           ... use nested attribute 'id' under 'userInfo' top level attribute (second segment not using brackets)
     *     ['user.info']['user.id']  ... use nested attribute 'user.id' under 'user.info' top level attribute
     *     ['user.info'].['user.id'] ... use nested attribute 'user.id' under 'user.info' top level attribute (optional dot)
     * </pre>
     *
     * @param spec Claim specification
     * @return Result containing either a claim with top level attribute name or a JsonPathQuery object
     */
    private static Extractor parseClaimSpec(String spec) {
        spec = spec == null ? null : spec.trim();
        if (spec == null || spec.isEmpty()) {
            return null;
        }

        if (!spec.startsWith("[")) {
            return new Extractor(spec);
        }

        return new Extractor(JsonPathQuery.parse(spec));
    }

    static class Extractor {

        private final String attributeName;
        private final JsonPathQuery query;

        private Extractor(JsonPathQuery query) {
            this.query = query;
            this.attributeName = null;
        }

        private Extractor(String attributeName) {
            this.attributeName = attributeName;
            this.query = null;
        }

        String getAttributeName() {
            return attributeName;
        }

        JsonPathQuery getJSONPathQuery() {
            return query;
        }

        @Override
        public String toString() {
            return query != null ? query.toString() : attributeName;
        }
    }
}
