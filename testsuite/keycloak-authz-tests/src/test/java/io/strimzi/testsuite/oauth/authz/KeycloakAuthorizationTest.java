/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for OAuth authentication using Keycloak + Keycloak Authorization Services based authorization
 *
 * This test assumes there are multiple listeners configured with OAUTHBEARER or PLAIN support, but each configured differently
 * - configured with different options, or different realm.
 *
 * There is KeycloakRBACAuthorizer configured on the Kafka broker.
 */
@RunWith(Arquillian.class)
public class KeycloakAuthorizationTest {

    @Test
    public void doTest() throws Exception {

        logStart("KeycloakAuthorizationTest :: JwtValidationAuthzTest");
        new BasicTest("kafka:9092", false).doTest();

        logStart("KeycloakAuthorizationTest :: IntrospectionValidationAuthzTest");
        new BasicTest("kafka:9093", false).doTest();

        logStart("KeycloakAuthorizationTest :: OAuthOverPlain + JwtValidationAuthzTest");
        new OAuthOverPlainTest("kafka:9094", true).doTest();

        logStart("KeycloakAuthorizationTest :: OAuthOverPlain + IntrospectionValidationAuthzTest");
        new OAuthOverPlainTest("kafka:9095", true).doTest();

        // This test has to be the last one - it changes the team-a-client, and team-b-client permissions in Keycloak
        logStart("KeycloakAuthorizationTest :: JwtValidationAuthzTest + RefreshGrants");
        new RefreshTest("kafka:9096", false).doTest();
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
        System.out.println();
    }
}
