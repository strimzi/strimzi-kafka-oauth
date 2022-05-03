/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class MockScheduledFuture<T> implements ScheduledFuture<T> {

    private final long scheduledAt;
    private final CompletableFuture<T> future;

    MockScheduledFuture(CompletableFuture<T> future, long scheduledAt) {
        this.future = future;
        this.scheduledAt = scheduledAt;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long remaining = scheduledAt - System.currentTimeMillis();
        switch (unit) {
            case MILLISECONDS:
                return remaining;
            case SECONDS:
                return TimeUnit.MILLISECONDS.toSeconds(remaining);
            case MINUTES:
                return TimeUnit.MILLISECONDS.toMinutes(remaining);
            case HOURS:
                return TimeUnit.MILLISECONDS.toHours(remaining);
            case DAYS:
                return TimeUnit.MILLISECONDS.toDays(remaining);
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit: " + unit);
        }
    }

    @Override
    public int compareTo(Delayed o) {
        long diff = scheduledAt - o.getDelay(TimeUnit.MILLISECONDS);
        return diff < 0 ? -1 : diff > 0 ? 1 : 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }
}
