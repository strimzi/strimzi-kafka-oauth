/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import org.apache.kafka.common.security.auth.KafkaPrincipal;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * A mechanism for OAuth over PLAIN to associate credentials with the PlainSaslServer
 */
public class Credentials {

    /**
     * There seems to be no way to associate a principal with PlainSaslServer at authentication phase.
     * The only information we have available there is clientId, and a secret.
     * Multiple connections established concurrently will each store their own validated KafkaPrincipal indexed by clientId.
     * Connections with the same clientId will each receive one validated access token.
     * The flow should guarantee that each store is followed by a corresponding take.
     */
    private Map<String, LinkedList<KafkaPrincipal>> validatedCredentials = new HashMap<>();

    /**
     * Store credentials to communicate them from PLAIN callback handler to OAuthKafkaPrincipalBuilder when OAuth over PLAIN is used.
     *
     * @param clientId A clientId as passed to the 'username' parameter of SASL_PLAIN mechanism
     * @param principal The OAuthKafkaPrincipal containing the validated token
     */
    public synchronized void storeCredentials(String clientId, KafkaPrincipal principal) {
        LinkedList<KafkaPrincipal> queue = validatedCredentials.computeIfAbsent(clientId, k -> new LinkedList<>());
        queue.add(principal);
    }

    /**
     * Take credentials in order to associate them with PlainSaslServer
     *
     * @param clientId A clientId as passed to the 'username' parameter of SASL_PLAIN mechanism
     * @return Stored OAuthKafkaPrincipal
     */
    public synchronized KafkaPrincipal takeCredentials(String clientId) {
        LinkedList<KafkaPrincipal> queue = validatedCredentials.get(clientId);
        if (queue == null) {
            return null;
        }

        KafkaPrincipal result = queue.poll();

        if (queue.size() == 0) {
            validatedCredentials.remove(clientId);
        }

        return result;
    }
}
