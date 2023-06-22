/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A helper class used to maintain per-user-id semaphores to implement the logic where one thread executes fetching of grants
 * while other threads wait and reuse the results.
 * <p>
 * <pre>
 *     Semaphores.SemaphoreResult<JsonNode> semaphore = semaphores.acquireSemaphore(key);
 *
 *     // Try to acquire semaphore
 *     if (semaphore.acquired()) {
 *         // If acquired
 *         try {
 *             // Obtain result
 *             JsonNode result = getResult();
 *
 *             // Make results available to other threads that didn't manage to acquire the semaphore
 *             semaphore.future().complete(result);
 *             return result;
 *
 *         } catch (Throwable t) {
 *             semaphore.future().completeExceptionally(t);
 *             throw t;
 *         } finally {
 *             // Release the semaphore in finally block
 *             semaphores.releaseSemaphore(userId);
 *         }
 *     } else {
 *         // Wait on the thread that acquired the semaphore to provide the result
 *         return semaphore.future().get();
 *     }
 * </pre>
 *
 * @param <T> A result type (e.g. JsonNode)
 */
class Semaphores<T> {

    private final ConcurrentHashMap<String, Semaphore<T>> futures = new ConcurrentHashMap<>();

    /**
     * Call this method so acquire semaphore for the key
     *
     * @param key The key
     * @return SemaphoreResult which contains information on whether the semaphore was acquired, and Future with the results
     */
    SemaphoreResult<T> acquireSemaphore(String key) {
        Semaphore<T> semaphore = futures.computeIfAbsent(key, v -> new Semaphore<>());
        return new SemaphoreResult<>(semaphore);
    }

    /**
     * Call this method in finally block to release the semaphore for the key
     *
     * @param key The key
     */
    void releaseSemaphore(String key) {
        futures.remove(key);
    }

    /**
     * Implementation of a semaphore.
     * <p>
     * Only the first call to {@link #tryAcquire()} will return <code>true</code>
     *
     * @param <T> Future result type
     */
    static class Semaphore<T> {

        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicBoolean acquired = new AtomicBoolean(true);

        private Semaphore() {}

        private boolean tryAcquire() {
            return acquired.getAndSet(false);
        }
    }

    /**
     * An object which contains information on whether the semaphore was acquired, and Future with the results.
     *
     * @param <T> Future result type
     */
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