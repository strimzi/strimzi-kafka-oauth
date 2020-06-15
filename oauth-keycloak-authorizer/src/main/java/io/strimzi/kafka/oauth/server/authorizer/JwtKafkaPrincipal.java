/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.security.auth.KafkaPrincipal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This class uses the KafkaPrincipal object to store additional info obtained at session authentication time,
 * and required later by a custom authorizer.
 *
 * This class is the only notion of client session that we can get. Kafka code holds on to it for as long as the session is alive,
 * and then the object can be garbage collected.
 *
 * Any additional fields should not be included in equals / hashcode check. If they are, that will break re-authentication.
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public final class JwtKafkaPrincipal extends KafkaPrincipal {

    private final BearerTokenWithPayload jwt;

    public JwtKafkaPrincipal(String principalType, String name) {
        this(principalType, name, null);
    }

    public JwtKafkaPrincipal(String principalType, String name, BearerTokenWithPayload jwt) {
        super(principalType, name);
        this.jwt = jwt;
    }

    public BearerTokenWithPayload getJwt() {
        return jwt;
    }
}
