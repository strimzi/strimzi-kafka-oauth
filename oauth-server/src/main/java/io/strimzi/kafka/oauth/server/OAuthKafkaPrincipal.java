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
 * <p>
 * Any additional fields should not be included in equals / hashcode check. If they are, that will break re-authentication.
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public final class OAuthKafkaPrincipal extends KafkaPrincipal {

    private final BearerTokenWithPayload jwt;
    private final Set<String> groups;

    /**
     * Create a new instance, and extract groups info from the passed {@link BearerTokenWithPayload}
     *
     * @param principalType Principal type (e.g. USER)
     * @param name A name
     * @param jwt <code>BearerTokenWithPayload</code> object with token info
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public OAuthKafkaPrincipal(String principalType, String name, BearerTokenWithPayload jwt) {
        super(principalType, name);
        this.jwt = jwt;
        Set<String> parsedGroups = jwt != null ? jwt.getGroups() : null;

        this.groups = parsedGroups == null ? null : Collections.unmodifiableSet(parsedGroups);
    }

    /**
     * Get the token info
     *
     * @return <code>BearerTokenWithPayload</code> instance
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public BearerTokenWithPayload getJwt() {
        return jwt;
    }

    /**
     * Get the initialised groups associated with this principal
     *
     * @return Set of groups
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public String toString() {
        return "OAuthKafkaPrincipal(" + super.toString() + ", groups: " + groups + ", session: " + (jwt != null ? jwt.getSessionId() : "") +
                ", token: " + (jwt != null ? mask(jwt.value()) : "") + ")";
    }
}
