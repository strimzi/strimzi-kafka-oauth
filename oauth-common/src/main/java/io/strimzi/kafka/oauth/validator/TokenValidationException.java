/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import java.util.Locale;

public class TokenValidationException extends ValidationException {

    private String status;

    {
        status(Status.INVALID_TOKEN);
    }

    public TokenValidationException(String message) {
        super(message);
    }

    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    TokenValidationException status(Status status) {
        this.status = status.value();
        return this;
    }

    public String status() {
        return status;
    }


    public enum Status {
        INVALID_TOKEN,
        EXPIRED_TOKEN,
        UNSUPPORTED_TOKEN_TYPE;

        public String value() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
}
