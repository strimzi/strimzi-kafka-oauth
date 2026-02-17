/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

/**
 * Runtime exception thrown when log line reading fails.
 */
public class LogLineReaderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new instance with a message.
     *
     * @param message The error message
     */
    public LogLineReaderException(String message) {
        super(message);
    }

    /**
     * Create a new instance with a message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public LogLineReaderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new instance with a cause.
     *
     * @param cause The underlying cause
     */
    public LogLineReaderException(Throwable cause) {
        super(cause);
    }
}
