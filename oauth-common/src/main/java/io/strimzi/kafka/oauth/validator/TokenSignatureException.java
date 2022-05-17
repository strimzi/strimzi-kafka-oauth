/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

public class TokenSignatureException extends TokenValidationException {

    {
        status(Status.INVALID_TOKEN);
    }

    public TokenSignatureException(String message) {
        super(message);
    }

    public TokenSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
