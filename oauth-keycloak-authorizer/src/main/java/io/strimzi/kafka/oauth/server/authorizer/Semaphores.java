/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class Semaphores<T> {

    private final ConcurrentHashMap<String, SemaphoreFuture<T>> futures = new ConcurrentHashMap<>();

    SemaphoreFuture<T> acquireSemaphore(String key) {
        return futures.computeIfAbsent(key, v -> new SemaphoreFuture<>());
    }

    void releaseSemaphore(String key) {
        futures.remove(key);
    }

    class SemaphoreFuture<S> extends CompletableFuture<S> {

        private final AtomicBoolean semaphore = new AtomicBoolean(true);

        private SemaphoreFuture() {}

        public boolean tryAcquire() {
            return semaphore.getAndSet(false);
        }
    }
}