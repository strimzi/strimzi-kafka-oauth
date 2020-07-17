/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sessions entries should automatically get cleared as KafkaPrincipals for the sessions get garbage collected by JVM.
 * The size of `activeSessions` at any moment in time should be about the number of currently active sessions.
 */
public class Sessions {

    private static final Object NONE = new Object();

    /**
     * The map of all 'sessions' in the form of BearerTokenWithPayload - the custom extension of OAuthBearerToken object
     * produced by our Validators at authentication time, which to us represents a session context.
     * The WeakHashMap is used to not prevent JVM from garbage collecting the completed and otherwise terminated sessions,
     * while we're still able to iterate over the active sessions.
     */
    private Map<BearerTokenWithPayload, Object> activeSessions = Collections.synchronizedMap(new WeakHashMap<>());

    public void put(BearerTokenWithPayload token) {
        activeSessions.put(token, NONE);
    }

    public void remove(BearerTokenWithPayload token) {
        activeSessions.remove(token);
    }

    public List<SessionFuture> executeTask(ExecutorService executor, Predicate<BearerTokenWithPayload> filter,
                                           Consumer<BearerTokenWithPayload> task) {
        cleanupExpired();

        // In order to prevent the possible ConcurrentModificationException in the middle of using an iterator
        // we first make a local copy, then iterate over the copy
        ArrayList<BearerTokenWithPayload> values = new ArrayList<>(activeSessions.keySet());

        List<SessionFuture> results = new ArrayList<>(values.size());
        for (BearerTokenWithPayload token: values) {
            if (filter.test(token)) {
                SessionFuture<?> current = new SessionFuture<>(token, executor.submit(() -> task.accept(token)));
                results.add(current);
            }
        }

        return results;
    }

    public void cleanupExpired() {
        // In order to prevent the possible ConcurrentModificationException in the middle of using an iterator
        // we first make a local copy, then iterate over the copy
        ArrayList<BearerTokenWithPayload> values = new ArrayList<>(activeSessions.keySet());

        long now = System.currentTimeMillis();

        // Remove expired
        for (BearerTokenWithPayload token: values) {
            if (token.lifetimeMs() <= now) {
                activeSessions.remove(token);
            }
        }
    }
}
