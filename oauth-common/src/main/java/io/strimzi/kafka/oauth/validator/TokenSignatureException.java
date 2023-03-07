/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

/**
 * A runtime exception that signals an invalid token signature
 */
public class TokenSignatureException extends TokenValidationException {

    {
        status(Status.INVALID_TOKEN);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     */
    public TokenSignatureException(String message) {
        super(message);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param cause A triggering cause of this exception
     */
    public TokenSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
