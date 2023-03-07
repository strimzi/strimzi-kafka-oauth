/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import org.apache.kafka.common.security.auth.KafkaPrincipal;

import javax.security.sasl.SaslServer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * An in-memory cache of 'sessions' and their associated principals.
 * Used to transfer the principal information between SASL authentication layer and PrincipalBuilder layer.
 */
public class Principals {

    private final Map<SaslServer, KafkaPrincipal> activeServers = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Put a new principal into the cache
     *
     * @param srv a SaslServer instance
     * @param principal a KafkaPrincipal
     */
    public void putPrincipal(SaslServer srv, KafkaPrincipal principal) {
        activeServers.put(srv, principal);
    }

    /**
     * Get the new principal from the cache
     *
     * @param srv a SaslServer principal
     * @return an associated KafkaPrincipal
     */
    public KafkaPrincipal getPrincipal(SaslServer srv) {
        return activeServers.get(srv);
    }
}
