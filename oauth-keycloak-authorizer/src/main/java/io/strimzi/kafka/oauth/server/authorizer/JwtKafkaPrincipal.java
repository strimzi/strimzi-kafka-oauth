/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.security.auth.KafkaPrincipal;

public class JwtKafkaPrincipal extends KafkaPrincipal {

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

    @Override
    public boolean equals(Object o) {
        // We don't care about jwt field for equals comparison
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
