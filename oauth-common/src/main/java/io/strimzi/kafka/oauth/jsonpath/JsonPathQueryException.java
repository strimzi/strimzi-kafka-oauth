/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

/**
 * A runtime exception signalling a syntactic or semantic error during JsonPathQuery or JsonPathFilterQuery parsing
 */
public class JsonPathQueryException extends RuntimeException {

    /**
     * Create a new instance
     *
     * @param message Error message
     * @param cause The original exception
     */
    public JsonPathQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
