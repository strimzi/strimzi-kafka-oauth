/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import org.apache.kafka.common.errors.SaslAuthenticationException;

/**
 * This class is used to communicate a SaslAuthenticationException in a way that also signals that any logging
 * has already been performed, and should not be done again in the delegating class.
 *
 * Specifically, the <em>JaasServerOauthOverPlainValidatorCallbackHandler</em> class delegates token validation to
 * {@link JaasServerOauthValidatorCallbackHandler} class which upon failed validation already logs the exception.
 *
 * When OAUTHBEARER is used, the {@link #getMessage()} method is invoked by <em>OAuthBearerSaslServer</em>, and the message
 * is included in server response to the client.
 */
public class OAuthSaslAuthenticationException extends SaslAuthenticationException {

    /**
     * An error id
     */
    private final String errId;

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param errId An error id that is logged to facilitate debugging
     */
    public OAuthSaslAuthenticationException(String message, String errId) {
        super(message);
        this.errId = errId;
    }

    /**
     * Create a new instance
     *
     * @param message An error message
     * @param errId An error id that is logged to facilitate debugging
     * @param cause A triggering cause of this exception
     */
    public OAuthSaslAuthenticationException(String message, String errId, Throwable cause) {
        super(message, cause);
        this.errId = errId;
    }

    /**
     * Get the error id
     *
     * @return An error id
     */
    public String getErrId() {
        return errId;
    }

    /**
     * Get the error message with error id attached
     *
     * @return An error message
     */
    @Override
    public String getMessage() {
        return super.getMessage() + " (ErrId: " + errId + ")";
    }
}
