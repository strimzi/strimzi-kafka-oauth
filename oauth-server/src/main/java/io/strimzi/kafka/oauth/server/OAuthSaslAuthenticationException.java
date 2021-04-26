/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import org.apache.kafka.common.errors.SaslAuthenticationException;

import static io.strimzi.kafka.oauth.common.LogUtil.getAllCauseMessages;

public class OAuthSaslAuthenticationException extends SaslAuthenticationException {

    private String errId;
    private boolean includeDetails = true;

    public OAuthSaslAuthenticationException(String message, String errId) {
        super(message);
        this.errId = errId;
    }

    public OAuthSaslAuthenticationException(String message, String errId, boolean includeDetails, Throwable cause) {
        super(message, cause);
        this.errId = errId;
        this.includeDetails = includeDetails && cause != null;
    }

    public String getErrId() {
        return errId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (ErrId: " + errId + ")" + (includeDetails ? ": " + getAllCauseMessages(getCause()) : "");
    }
}
