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
import java.util.Set;

public class TokenInfo {

    public static final String SCOPE = "scope";
    public static final String IAT = "iat";
    public static final String EXP = "exp";
    public static final String ISS = "iss";
    public static final String TYP = "typ";
    public static final String TOKEN_TYPE = "token_type";
    public static final String AUD = "aud";

    private final String token;
    private final long expiresAt;
    private final String principal;
    private final Set<String> groups;
    private final long issuedAt;
    private Set<String> scopes = Collections.emptySet();
    private ObjectNode payload;

    public TokenInfo(JsonNode payload, String token, String principal) {
        this(payload, token, principal, null);
    }

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public TokenInfo(JsonNode payload, String token, String principal, Set<String> groups) {
        this(token,
                payload.has(SCOPE) ? payload.get(SCOPE).asText() : null,
                principal,
                groups,
                payload.has(IAT) ? payload.get(IAT).asInt(0) * 1000L : 0L,
                payload.get(EXP).asInt(0) * 1000L);

        if (!(payload instanceof ObjectNode)) {
            throw new IllegalArgumentException("Unexpected JSON Node type (not ObjectNode): " + payload.getClass());
        }
        this.payload = (ObjectNode) payload;
    }

    public TokenInfo(String token, String scope, String principal, Set<String> groups, long issuedAtMs, long expiresAtMs) {
        this.token = token;
        this.principal = principal;
        this.groups = groups != null ? Collections.unmodifiableSet(groups) : null;
        this.issuedAt = issuedAtMs;
        this.expiresAt = expiresAtMs;

        String[] parsedScopes = scope != null ? scope.split(" ") : new String[0];
        scopes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(parsedScopes)));
    }

    public String token() {
        return token;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Set<String> scope() {
        return scopes;
    }

    public long expiresAtMs() {
        return expiresAt;
    }

    public String principal() {
        return principal;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Set<String> groups() {
        return groups;
    }

    public long issuedAtMs() {
        return issuedAt;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ObjectNode payload() {
        return payload;
    }
}
