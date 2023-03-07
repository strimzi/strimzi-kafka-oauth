/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

/**
 * An exception used to report a background job failure
 */
public class ServiceException extends RuntimeException {

    /**
     * Create a new instance
     *
     * @param message An error message
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param cause A cause exception
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
