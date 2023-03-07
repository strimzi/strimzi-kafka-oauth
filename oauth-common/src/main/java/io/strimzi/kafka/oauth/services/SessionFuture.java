/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A Java <code>Future</code> implementation used in conjunction with {@link Sessions#executeTask(ExecutorService, Predicate, Consumer)} method
 *
 * @param <T> A generic type of the Future result
 */
public class SessionFuture<T> implements Future<T> {

    private final Future<T> delegate;
    private final BearerTokenWithPayload token;

    /**
     * Create a new instance
     *
     * @param token Token object representing a session
     * @param future Original future instance to wrap
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public SessionFuture(BearerTokenWithPayload token, Future<T> future) {
        this.token = token;
        this.delegate = future;
    }

    /**
     * Get a <code>BearerTokenWithPayload</code> object representing a session
     *
     * @return A token instance
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public BearerTokenWithPayload getToken() {
        return token;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }
}
