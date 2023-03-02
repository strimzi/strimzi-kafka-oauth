/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import java.util.Locale;

/**
 * A runtime exception used to signal an invalid token
 */
public class TokenValidationException extends ValidationException {

    /**
     * Validation check error status for this instance
     */
    private String status;

    {
        status(Status.INVALID_TOKEN);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     */
    public TokenValidationException(String message) {
        super(message);
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param cause A triggering cause of this exception
     */
    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Set the validation check status that caused validation failure
     *
     * @param status {@link Status} enum value
     * @return This exception
     */
    TokenValidationException status(Status status) {
        this.status = status.value();
        return this;
    }

    /**
     * Get the validation check error status for this exception
     *
     * @return A token validation error status as String
     */
    public String status() {
        return status;
    }

    /**
     * An Enum for different token validation error statuses
     */
    public enum Status {
        /**
         * Token is invalid
         */
        INVALID_TOKEN,
        /**
         * Token is expired
         */
        EXPIRED_TOKEN,
        /**
         * Token is rejected based on the token type
         */
        UNSUPPORTED_TOKEN_TYPE;

        /**
         * Get enum value as a lowercase String
         *
         * @return a lowercase string
         */
        public String value() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
}
