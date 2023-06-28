/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

/**
 * A static holder for the KeycloakAuthorizerSingleton instance
 */
@SuppressWarnings("deprecation")
public class KeycloakAuthorizerService {

    private static KeycloakRBACAuthorizer instance;

    /**
     * Get the current singleton instance
     *
     * @return The instance previously set by {@link #setInstance(KeycloakRBACAuthorizer)}
     */
    static KeycloakRBACAuthorizer getInstance() {
        return instance;
    }

    /**
     * Set the current KeycloakRBACAuthorizer instance as singleton
     *
     * @param instance The new instance
     */
    static void setInstance(KeycloakRBACAuthorizer instance) {
        KeycloakAuthorizerService.instance = instance;
    }

    static void clearInstance() {
        instance = null;
    }
}
