/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.kafka.oauth.common;

/**
 * A TokenProvider that contains an immutable token that is returned every time a {@link io.strimzi.kafka.oauth.common.StaticTokenProvider#token()} method is called.
 */
public class StaticTokenProvider implements TokenProvider {
    private final String token;

    /**
     * Create a new instance with a token that never changes
     *
     * @param token A token
     */
    public StaticTokenProvider(final String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
