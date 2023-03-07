/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

/**
 * The exception used to signal a configuration error
 */
public class ConfigException extends RuntimeException {

    /**
     * Create a new instance with the specified error message
     *
     * @param message The error message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Create a new instance with the specified error message, and a cause
     *
     * @param message The error message
     * @param cause The exception cause
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
