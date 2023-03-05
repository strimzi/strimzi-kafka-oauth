/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * This extension of OAuthBearerToken provides a way to associate any additional information with the token
 * at run time, that is cached for the duration of the client session.
 *
 * This class is the only notion of client session that we can get. Kafka code holds on to it for as long as the session is alive,
 * and then the object can be garbage collected.
 *
 * Successful re-authentication starts a new session without disconnecting the current connection, avoiding the need to re-establish
 * any existing TLS connection for example.
 *
 * Token is instantiated during authentication, but the 'payload' methods can be accessed later by custom extensions.
 * For example, it can be used by a custom authorizer to cache a parsed JWT token payload or to cache authorization grants for current session.
 */
public interface BearerTokenWithPayload extends OAuthBearerToken {

    /**
     * Get the usage dependent object previously associated with this instance by calling {@link BearerTokenWithPayload#setPayload(com.fasterxml.jackson.databind.JsonNode)}
     *
     * @return The associated object
     */
    JsonNode getPayload();

    /**
     * Associate a usage dependent object with this instance
     *
     * @param payload The object to associate with this instance
     */
    void setPayload(JsonNode payload);

    /**
     * Get groups associated with this token (principal).
     *
     * @return The groups for the user
     */
    Set<String> getGroups();

    /**
     * The token claims as a JSON object. For JWT tokens it contains the content of the JWT Payload part of the token.
     * If introspection is used, it contains the introspection endpoint response.
     *
     * @return Token content / details as a JSON object
     */
    ObjectNode getJSON();

    /**
     * This method returns an id of the current instance of this object.
     * It is used for debugging purposes - e.g. logging that allows tracking of an individual instance
     * of this object through logs.
     *
     * @return An int identifying this instance
     */
    default int getSessionId() {
        return System.identityHashCode(this);
    }
}
