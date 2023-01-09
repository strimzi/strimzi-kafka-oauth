/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class Semaphores<T> {

    private final ConcurrentHashMap<String, Semaphore<T>> futures = new ConcurrentHashMap<>();

    SemaphoreResult<T> acquireSemaphore(String key) {
        Semaphore<T> semaphore = futures.computeIfAbsent(key, v -> new Semaphore<>());
        return new SemaphoreResult<>(semaphore);
    }

    void releaseSemaphore(String key) {
        futures.remove(key);
    }

    static class Semaphore<T> {

        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicBoolean acquired = new AtomicBoolean(true);

        private Semaphore() {}

        private boolean tryAcquire() {
            return acquired.getAndSet(false);
        }
    }

    static class SemaphoreResult<T> {

        private final boolean acquired;
        private final CompletableFuture<T> future;

        private SemaphoreResult(Semaphore<T> semaphore) {
            this.acquired = semaphore.tryAcquire();
            this.future = semaphore.future;
        }

        boolean acquired() {
            return acquired;
        }

        CompletableFuture<T> future() {
            return future;
        }
    }
}