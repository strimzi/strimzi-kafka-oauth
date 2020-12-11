/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.security.auth.KafkaPrincipal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;

/**
 * This class extends the KafkaPrincipal object to store additional info obtained at session authentication time,
 * and required later by a custom authorizer.
 *
 * Any additional fields should not be included in equals / hashcode check. If they are, that will break re-authentication.
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public final class OAuthKafkaPrincipal extends KafkaPrincipal {

    private static ThreadLocal<OAuthKafkaPrincipal> currentTL = new ThreadLocal<>();

    private final BearerTokenWithPayload jwt;

    public OAuthKafkaPrincipal(String principalType, String name) {
        this(principalType, name, null);
    }

    public OAuthKafkaPrincipal(String principalType, String name, BearerTokenWithPayload jwt) {
        super(principalType, name);
        this.jwt = jwt;
    }

    public BearerTokenWithPayload getJwt() {
        return jwt;
    }

    public static OAuthKafkaPrincipal takeFromThreadContext() {
        OAuthKafkaPrincipal current = currentTL.get();
        currentTL.remove();
        return current;
    }

    public static void setToThreadContext(OAuthKafkaPrincipal principal) {
        currentTL.set(principal);
    }

    @Override
    public String toString() {
        return "OAuthKafkaPrincipal(" + super.toString() + ", session:" + (jwt != null ? jwt.getSessionId() : "") +
                ", token:" + (jwt != null ? mask(jwt.value()) : "") + ")";
    }
}
