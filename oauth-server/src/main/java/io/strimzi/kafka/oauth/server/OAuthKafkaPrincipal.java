/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.security.auth.KafkaPrincipal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.Set;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;

/**
 * This class extends the KafkaPrincipal object to store additional info obtained at session authentication time,
 * and required later by a custom authorizer.
 *
 * Any additional fields should not be included in equals / hashcode check. If they are, that will break re-authentication.
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public final class OAuthKafkaPrincipal extends KafkaPrincipal {

    private final BearerTokenWithPayload jwt;
    private final Set<String> groups;

    public OAuthKafkaPrincipal(String principalType, String name) {
        this(principalType, name, (Set<String>) null);
    }

    public OAuthKafkaPrincipal(String principalType, String name, Set<String> groups) {
        super(principalType, name);
        this.jwt = null;

        this.groups = groups == null ? null : Collections.unmodifiableSet(groups);
    }

    public OAuthKafkaPrincipal(String principalType, String name, BearerTokenWithPayload jwt) {
        super(principalType, name);
        this.jwt = jwt;
        Set<String> parsedGroups = jwt.getGroups();

        this.groups = parsedGroups == null ? null : Collections.unmodifiableSet(parsedGroups);
    }

    public BearerTokenWithPayload getJwt() {
        return jwt;
    }

    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public String toString() {
        return "OAuthKafkaPrincipal(" + super.toString() + ", groups: " + groups + ", session: " + (jwt != null ? jwt.getSessionId() : "") +
                ", token: " + (jwt != null ? mask(jwt.value()) : "") + ")";
    }
}
