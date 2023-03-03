/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

/**
 * An interface defining a callback that receives success and failure callbacks with request times
 */
public interface MetricsHandler {

    /**
     * Called when a request was successful
     *
     * @param millis time spent for request in millis
     */
    void addSuccessRequestTime(long millis);

    /**
     * Called when a request has failed
     *
     * @param e An exception as a Throwable object
     * @param millis time spent for request in millis
     */
    void addErrorRequestTime(Throwable e, long millis);
}
