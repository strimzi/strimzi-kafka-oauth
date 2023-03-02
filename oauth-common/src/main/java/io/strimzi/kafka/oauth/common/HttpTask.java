/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.io.IOException;

/**
 * An interface that allows a started task to directly throw an IOException.
 *
 * It is primarily meant to be used in conjunction with the {@link HttpUtil#doWithRetries} method,
 * to perform a successful HTTP request to the authorization server, where the <code>HttpTask</code> defines an individual
 * single invocation.
 *
 * @param <T> A generic return type of the task result
 */
public interface HttpTask<T> {

    /**
     * Run a task, and return the result as an object or a null if successful, otherwise throw an exception.
     *
     * Any exception thrown (checked or unchecked) will result in a repeat attempt by {@link HttpUtil#doWithRetries}.
     *
     * @return A result object
     * @throws IOException An exception signalling a connectivity issue, a timeout or an unexpected response status.
     */
    T run() throws IOException;
}
