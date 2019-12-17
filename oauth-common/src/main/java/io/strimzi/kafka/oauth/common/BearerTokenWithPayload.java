/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;

/**
 * This extension of OAuthBearerToken provides a way to associate any additional information with the token
 * at run time, that is cached for the duration of the client session.
 *
 * Token is instanciated during authentication, but the 'payload' methods can be accessed later by custom extensions.
 * For example, it can be used by a custom authorizer to cache a parsed JWT token payload or to cache authorization grants for current session.
 */
public interface BearerTokenWithPayload extends OAuthBearerToken {

    Object getPayload();

    void setPayload(Object payload);

}
