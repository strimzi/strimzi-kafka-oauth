/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

/**
 * A contract for a class that provides a token
 */
public interface TokenProvider {

    /**
     * Get a token
     *
     * @return A token
     */
    String token();

}
