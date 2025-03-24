/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * TokenInfo encapsulates the information about the access token.
 * <p>
 * It can also be used for storing extra application information associated with the access token by directly
 * accessing the payload JSON object.
 */
public class TokenInfo {

    /**
     * "scope"
     */
    public static final String SCOPE = "scope";
    /**
     * "iat"
     */
    public static final String IAT = "iat";
    /**
     * "exp"
     */
    public static final String EXP = "exp";
    /**
     * "iss"
     */
    public static final String ISS = "iss";
    /**
     * "typ"
     */
    public static final String TYP = "typ";
    /**
     * "token_type"
     */
    public static final String TOKEN_TYPE = "token_type";
    /**
     * "aud"
     */
    public static final String AUD = "aud";

    private final String token;
    private final long expiresAt;
    private final String principal;
    private final Set<String> groups;
    private final long issuedAt;
    private final Set<String> scopes;
    private ObjectNode payload;

    /**
     * Create a new instance
     *
     * @param payload The body of the JWT token or composed of authorization server's introspection endpoint response
     * @param token The raw access token
     * @param principal The extracted user ID
     */
    public TokenInfo(JsonNode payload, String token, String principal) {
        this(payload, token, principal, null);
    }

    /**
     * Create a new instance
     *
     * @param payload The body of the JWT token or composed of authorization server's introspection endpoint response
     * @param token The raw access token
     * @param principal The extracted user ID
     * @param groups A set of groups extracted from JWT token or authorization server's inspect endpoint response
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    // See: https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei2-may-expose-internal-representation-by-incorporating-reference-to-mutable-object-ei-expose-rep2
    public TokenInfo(JsonNode payload, String token, String principal, Set<String> groups) {
        this(token,
                payload.has(SCOPE) ? payload.get(SCOPE).asText() : null,
                principal,
                groups,
                payload.has(IAT) ? payload.get(IAT).asLong(0) * 1000L : 0L,
                payload.get(EXP).asLong(0) * 1000L);

        if (!(payload instanceof ObjectNode)) {
            throw new IllegalArgumentException("Unexpected JSON Node type (not ObjectNode): " + payload.getClass());
        }
        // Causes EI_EXPOSE_REP2, but we want the payload to remain mutable
        // It should be fine without making the essentially unnecessary deep copy
        this.payload = (ObjectNode) payload;
    }

    /**
     *
     * @param token The raw access token
     * @param scope The scope returned by authorization server's inspect endpoint response
     * @param principal The extracted user ID
     * @param groups A set of groups extracted from JWT token or authorization server's inspect endpoint response
     * @param issuedAtMs The token's `issued at` time in millis
     * @param expiresAtMs The token's `expires at` time in millis
     */
    public TokenInfo(String token, String scope, String principal, Set<String> groups, long issuedAtMs, long expiresAtMs) {
        this(token,
                Collections.unmodifiableSet(new HashSet<>(Arrays.asList(scope != null ? scope.split(" ") : new String[0]))),
                principal,
                groups,
                issuedAtMs,
                expiresAtMs,
                null);
    }

    /**
     *
     * @param token The raw access token
     * @param scopes The list of scopes
     * @param principal The extracted user ID
     * @param groups A set of groups extracted from JWT token or authorization server's inspect endpoint response
     * @param issuedAtMs The token's `issued at` time in millis
     * @param expiresAtMs The token's `expires at` time in millis
     * @param payload The body of the JWT token or composed of authorization server's introspection endpoint response
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    // See: https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei2-may-expose-internal-representation-by-incorporating-reference-to-mutable-object-ei-expose-rep2
    public TokenInfo(String token, Set<String> scopes, String principal, Set<String> groups, long issuedAtMs, long expiresAtMs, JsonNode payload) {
        if (token == null) {
            throw new IllegalArgumentException("token can't be null");
        }
        if (principal == null) {
            throw new IllegalArgumentException("principal can't be null");
        }
        this.token = token;
        this.principal = principal;
        this.groups = groups != null ? Collections.unmodifiableSet(groups) : null;
        this.issuedAt = issuedAtMs;
        this.expiresAt = expiresAtMs;
        this.scopes = scopes;
        if (payload != null && !(payload instanceof ObjectNode)) {
            throw new IllegalArgumentException("Unexpected JSON Node type (not ObjectNode): " + payload.getClass());
        }
        this.payload = (ObjectNode) payload;
    }

    /**
     * Get raw access token
     *
     * @return Access token as String
     */
    public String token() {
        return token;
    }

    /**
     * Get scopes for this token
     *
     * @return A Set of scopes as strings
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    // See https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei-may-expose-internal-representation-by-returning-reference-to-mutable-object-ei-expose-rep
    public Set<String> scope() {
        // `scopes` is in fact never modifiable because it is wrapped with `Collections.unmodifiableSet()`
        return scopes;
    }

    /**
     * Get token expiry time in ISO millis time
     *
     * @return Long value representing time
     */
    public long expiresAtMs() {
        return expiresAt;
    }

    /**
     * Get a principal (user id) for this token
     *
     * @return User id as String
     */
    public String principal() {
        return principal;
    }

    /**
     * Get groups for this token
     *
     * @return Set of groups as strings
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    // See https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei-may-expose-internal-representation-by-returning-reference-to-mutable-object-ei-expose-rep
    public Set<String> groups() {
        // `groups` is in fact never modifiable because it is wrapped with `Collections.unmodifiableSet()`
        return groups;
    }

    /**
     * Get token creation time ISO millis
     *
     * @return Long value representing time
     */
    public long issuedAtMs() {
        return issuedAt;
    }

    /**
     * Get the payload object passed during construction.
     * <p>
     * The same instance, passed to the TokenInfo constructor is returned which makes it possible to add custom attributes
     * or make modifications during request processing.
     *
     * @return The payload object.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ObjectNode payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenInfo)) return false;
        TokenInfo tokenInfo = (TokenInfo) o;
        return expiresAt == tokenInfo.expiresAt &&
                issuedAt == tokenInfo.issuedAt &&
                token.equals(tokenInfo.token) &&
                principal.equals(tokenInfo.principal) &&
                Objects.equals(groups, tokenInfo.groups) &&
                Objects.equals(scopes, tokenInfo.scopes) &&
                Objects.equals(payload, tokenInfo.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, expiresAt, principal, groups, issuedAt, scopes, payload);
    }
}
