/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.services;

import org.apache.kafka.common.security.auth.KafkaPrincipal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Sessions entries should automatically get cleared as KafkaPrincipals for the sessions get garbage collected by JVM.
 * The size of `activeSessions` at any moment in time should be about the number of currently active sessions.
 */
public class Sessions {

    private ExecutorService executor;

    private Map<KafkaPrincipal, SessionInfo> activeSessions = Collections.synchronizedMap(new WeakHashMap<>());

    public Sessions(ExecutorService executor) {
        this.executor = executor;
    }

    public void put(KafkaPrincipal session, SessionInfo token) {
        activeSessions.put(session, token);
    }

    public SessionInfo get(KafkaPrincipal session) {
        return activeSessions.get(session);
    }

    public void executeTask(Consumer<SessionInfo> task) {

        // In order to prevent the possible ConcurrentModificationException in the middle of using an iterator
        // we first make a local copy, then iterate over the copy
        ArrayList<SessionInfo> values;
        synchronized (activeSessions) {
            values = new ArrayList<>(activeSessions.values());
        }

        for (SessionInfo w: values) {
            executor.submit(() -> task.accept(w));
        }
    }
}
