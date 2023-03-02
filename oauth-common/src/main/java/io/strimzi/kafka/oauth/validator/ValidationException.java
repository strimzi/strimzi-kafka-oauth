/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

/**
 * A runtime exception used to signal an error while performing token validation
 */
public class ValidationException extends RuntimeException {

    /**
     * Create new instance
     *
     * @param message An error message
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param cause A triggering cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
