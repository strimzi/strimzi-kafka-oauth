/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TokenInfo {

    public static final String SCOPE = "scope";
    public static final String IAT = "iat";
    public static final String EXP = "exp";
    public static final String ISS = "iss";
    public static final String TYP = "typ";
    public static final String TOKEN_TYPE = "token_type";
    public static final String AUD = "aud";

    private String token;
    private Set<String> scopes = new HashSet<>();
    private long expiresAt;
    private String principal;
    private List<String> groups;
    private long issuedAt;
    private ObjectNode payload;

    public TokenInfo(JsonNode payload, String token, String principal) {
        this(payload, token, principal, null);
    }

    public TokenInfo(JsonNode payload, String token, String principal, List<String> groups) {
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

    public TokenInfo(String token, String scope, String principal, List<String> groups, long issuedAtMs, long expiresAtMs) {
        this.token = token;
        this.principal = principal;
        this.groups = groups != null ? Collections.unmodifiableList(groups) : null;
        this.issuedAt = issuedAtMs;
        this.expiresAt = expiresAtMs;

        String[] parsedScopes = scope != null ? scope.split(" ") : new String[0];
        for (String s: parsedScopes) {
            scopes.add(s);
        }
        scopes = Collections.unmodifiableSet(scopes);
    }

    public String token() {
        return token;
    }

    public Set<String> scope() {
        return scopes;
    }

    public long expiresAtMs() {
        return expiresAt;
    }

    public String principal() {
        return principal;
    }

    public List<String> groups() {
        return groups;
    }

    public long issuedAtMs() {
        return issuedAt;
    }

    public ObjectNode payload() {
        return payload;
    }
}
