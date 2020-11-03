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

public class Principals {

    private Map<SaslServer, KafkaPrincipal> activeServers = Collections.synchronizedMap(new WeakHashMap<>());

    public void putPrincipal(SaslServer srv, KafkaPrincipal principal) {
        activeServers.put(srv, principal);
    }

    public KafkaPrincipal getPrincipal(SaslServer srv) {
        return activeServers.get(srv);
    }
}
