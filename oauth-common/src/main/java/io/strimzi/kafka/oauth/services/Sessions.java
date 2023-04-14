/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;

/**
 * Sessions entries should automatically get cleared as KafkaPrincipals for the sessions get garbage collected by JVM.
 * The size of `activeSessions` at any moment in time should be about the number of currently active sessions.
 * They may also get removed by broker-side plugins like custom authorizers when it is determined that the token has expired.
 */
public class Sessions {

    private static final Logger log = LoggerFactory.getLogger(Sessions.class);

    private static final Object NONE = new Object();

    /**
     * The map of all 'sessions' in the form of BearerTokenWithPayload - the custom extension of OAuthBearerToken object
     * produced by our Validators at authentication time, which to us represents a session context.
     * The WeakHashMap is used to not prevent JVM from garbage collecting the completed and otherwise terminated sessions,
     * while we're still able to iterate over the active sessions.
     */
    private final Map<BearerTokenWithPayload, Object> activeSessions = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Put a new token object into sessions cache
     *
     * @param token A token to add
     */
    public void put(BearerTokenWithPayload token) {
        activeSessions.put(token, NONE);
    }

    /**
     * Remove a token from sessions cache
     *
     * @param token A token to remove
     */
    public void remove(BearerTokenWithPayload token) {
        activeSessions.remove(token);
    }

    /**
     * Remove all the active sessions for the passed access token
     *
     * @param accessToken access token for which to remove the sessions
     */
    public void removeAllWithMatchingAccessToken(String accessToken) {
        // In order to prevent the possible ConcurrentModificationException in the middle of using an iterator
        // we first make a local copy, then iterate over the copy
        ArrayList<BearerTokenWithPayload> values = new ArrayList<>(activeSessions.keySet());

        // Remove matching
        for (BearerTokenWithPayload token: values) {
            if (accessToken.equals(token.value())) {
                activeSessions.remove(token);
                if (log.isDebugEnabled()) {
                    log.debug("Removed invalid sessions from sessions map (userId: {}, session: {}, token: {}). Will not refresh its grants any more.",
                            token.principalName(), token.getSessionId(), mask(token.value()));
                }
            }
        }
    }

    /**
     * Iterate over all active sessions (represented by stored token objects) applying a filter and submit a task to the passed executor for each passing token.
     * Return a list of {@link SessionFuture} instances.
     *
     * @param executor An Executor to use for submitting tasks
     * @param filter A filter to decide if the task should be submitted for specific token
     * @param task Logic to run on the token
     * @return A list of SessionFuture instances
     */
    public List<SessionFuture<?>> executeTask(ExecutorService executor, Predicate<BearerTokenWithPayload> filter,
                                           Consumer<BearerTokenWithPayload> task) {
        cleanupExpired();

        // In order to prevent the possible ConcurrentModificationException in the middle of using an iterator
        // we first make a local copy, then iterate over the copy
        ArrayList<BearerTokenWithPayload> values = new ArrayList<>(activeSessions.keySet());

        List<SessionFuture<?>> results = new ArrayList<>(values.size());
        for (BearerTokenWithPayload token: values) {
            if (filter.test(token)) {
                SessionFuture<?> current = new SessionFuture<>(token, executor.submit(() -> task.accept(token)));
                results.add(current);
            }
        }

        return results;
    }

    /**
     * Cleanup the sessions whose lifetime has expired
     */
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

    /**
     * Get a list of objects retrieved by applying the passed mapping function to the current active sessions set.
     * <p>
     * For example, you can get a list of all the princiapl names.
     *
     * @param mapper A mapping function
     * @return A list of mapped results
     * @param <T> A return type
     */
    public <T> List<T> map(Function<BearerTokenWithPayload, T> mapper) {
        cleanupExpired();
        ArrayList<BearerTokenWithPayload> values = new ArrayList<>(activeSessions.keySet());

        return values.stream().map(mapper).collect(Collectors.toList());
    }
}
