/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.keycloak.representations.AccessToken;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TokenInfo {

    private String token;
    private Set<String> scopes = new HashSet<>();
    private long expiresAt;
    private String subject;
    private long issuedAt;
    private AccessToken payload;

    public TokenInfo(AccessToken payload, String token) {
        this(token,
                payload.getScope(),
                payload.getSubject(),
                payload.getIat() == null ? 0 : payload.getIat() * 1000L,
                payload.getExp() == null ? 0 : payload.getExp() * 1000L);
        this.payload = payload;
    }

    public TokenInfo(String token, String scope, String subject, long issuedAtMs, long expiresAtMs) {
        this.token = token;
        this.subject = subject;
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

    public String subject() {
        return subject;
    }

    public long issuedAtMs() {
        return issuedAt;
    }

    public AccessToken payload() {
        return payload;
    }
}
