/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.common.TokenInfo;

/**
 * An interface specifying a TokenValidator contract
 */
public interface TokenValidator {

    /**
     * Validate the passed access token return it wrapped in TokenInfo with
     *
     * @param token An access token to validate
     * @return TokenInfo wrapping a valid token
     */
    TokenInfo validate(String token);

    /**
     * Return the id of this validator
     *
     * @return A validator id
     */
    String getValidatorId();

    /**
     * Close any allocated resources like background threads
     */
    void close();
}
