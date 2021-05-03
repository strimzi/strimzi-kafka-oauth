/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import org.apache.kafka.common.errors.SaslAuthenticationException;

public class OAuthSaslAuthenticationException extends SaslAuthenticationException {

    private String errId;

    public OAuthSaslAuthenticationException(String message, String errId) {
        super(message);
        this.errId = errId;
    }

    public OAuthSaslAuthenticationException(String message, String errId, Throwable cause) {
        super(message, cause);
        this.errId = errId;
    }

    public String getErrId() {
        return errId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (ErrId: " + errId + ")";
    }
}
