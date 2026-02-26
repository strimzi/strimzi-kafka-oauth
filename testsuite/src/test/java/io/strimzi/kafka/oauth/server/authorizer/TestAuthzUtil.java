/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

/**
 * This class overrides package-private class from OAuth library and has to be in that module structure!
 */
public class TestAuthzUtil {

    public static void clearKeycloakAuthorizerService() {
        KeycloakAuthorizerService.clearInstance();
    }
}
