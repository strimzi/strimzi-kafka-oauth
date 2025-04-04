/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

public class LogLineReaderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LogLineReaderException(String message) {
        super(message);
    }

    public LogLineReaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public LogLineReaderException(Throwable cause) {
        super(cause);
    }
}
