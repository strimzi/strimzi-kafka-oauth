/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

public class TestAuthzUtil {

    public static void clearKeycloakAuthorizerService() {
        KeycloakAuthorizerService.clearInstance();
    }
}
