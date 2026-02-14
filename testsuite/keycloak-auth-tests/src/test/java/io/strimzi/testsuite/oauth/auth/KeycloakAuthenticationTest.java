/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with SASL OAUTHBEARER mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with SASL PLAIN mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KeycloakAuthenticationTest {

    static final Logger log = LoggerFactory.getLogger(KeycloakAuthenticationTest.class);

    private final KeycloakAuthTestEnvironment env = new KeycloakAuthTestEnvironment();

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(env::getContainers);

    @BeforeAll
    public void setUp() {
        env.start();
    }

    @AfterAll
    public void tearDown() {
        env.stop();
    }

    @Test
    public void doTest() throws Exception {
        try {
            logStart("KeycloakAuthenticationTest :: BasicTests");
            new BasicTests(env.getKafka()).doTests();

            logStart("KeycloakAuthenticationTest :: OAuthOverPlainTests");
            OAuthOverPlainTests.doTests();

            logStart("KeycloakAuthenticationTest :: AudienceTests");
            AudienceTests.doTests();

            logStart("KeycloakAuthenticationTest :: CustomCheckTests");
            CustomCheckTests.doTests();

            logStart("KeycloakAuthenticationTest :: GroupsExtractionTests");
            new GroupsExtractionTests(env.getKafka()).doTests();

            logStart("KeycloakAuthenticationTest :: MultiSaslTests");
            MultiSaslTests.doTests();

            logStart("KeycloakAuthenticationTest :: JwtManipulationTests");
            new JwtManipulationTests().doTests();

        } catch (Throwable e) {
            log.error("Keycloak Authentication Test failed: ", e);
            throw e;
        }
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }
}
