/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz.kraft;

import io.strimzi.testsuite.oauth.authz.BasicTest;
import io.strimzi.testsuite.oauth.authz.ConfigurationTest;
import io.strimzi.testsuite.oauth.authz.FloodTest;
import io.strimzi.testsuite.oauth.authz.MetricsTest;
import io.strimzi.testsuite.oauth.authz.MultiSaslTest;
import io.strimzi.testsuite.oauth.authz.OAuthOverPlainTest;
import io.strimzi.testsuite.oauth.authz.RefreshTest;
import io.strimzi.testsuite.oauth.authz.ScramTest;
import io.strimzi.testsuite.oauth.authz.SingletonTest;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.strimzi.testsuite.oauth.authz.Common.waitForACLs;
import static io.strimzi.testsuite.oauth.common.TestUtil.logStart;

/**
 * Tests for OAuth authentication using Keycloak + Keycloak Authorization Services based authorization when KeycloakAuthorizer is configured on the Kafka broker running in KRaft mode
 * <p>
 * This test assumes there are multiple listeners configured with OAUTHBEARER or PLAIN support, but each configured differently
 * - configured with different options, or different realm.
 * <p>
 * There is KeycloakAuthorizer configured on the Kafka broker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KeycloakKRaftAuthorizationTests {

    private static final Logger log = LoggerFactory.getLogger(KeycloakKRaftAuthorizationTests.class);

    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    private static final String JWT_LISTENER = "localhost:9092";
    private static final String INTROSPECT_LISTENER = "localhost:9093";
    private static final String JWTPLAIN_LISTENER = "localhost:9094";
    private static final String INTROSPECTPLAIN_LISTENER = "localhost:9095";
    private static final String JWTREFRESH_LISTENER = "localhost:9096";

    @BeforeAll
    void setUp() {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @Order(1)
    public void doConfigurationTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: ConfigurationTest");
        new ConfigurationTest(environment.getKafka()).doTest();
    }

    @Test
    @Order(2)
    public void doMetricsTestPart1() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: MetricsTest (part 1)");
        MetricsTest.doTest();
    }

    @Test
    @Order(3)
    public void doMultiSaslTests() throws Exception {
        // Before running the rest of the tests, ensure ACLs have been added to Kafka cluster
        waitForACLs();

        logStart("KeycloakKRaftAuthorizationTest :: MultiSaslTests");
        new MultiSaslTest(environment.getKafka()).doTest();
    }

    @Test
    @Order(4)
    public void doScramTest() throws Exception {
        logStart("KeycloakAuthorizationTest :: ScramTest");
        new ScramTest().doTest();
    }

    @Test
    @Order(5)
    public void doJwtValidationAuthzTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: JwtValidationAuthzTest");
        new BasicTest(environment.getKafka(), JWT_LISTENER, false).doTest();
    }

    @Test
    @Order(6)
    public void doIntrospectionValidationAuthzTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: IntrospectionValidationAuthzTest");
        new BasicTest(environment.getKafka(), INTROSPECT_LISTENER, false).doTest();
    }

    @Test
    @Order(7)
    public void doMetricsTestPart2() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: MetricsTest (part 2)");
        MetricsTest.doTestValidationAndAuthorization();
    }

    @Test
    @Order(8)
    public void doOAuthOverPlainJwtTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPlain + JwtValidationAuthzTest");
        new OAuthOverPlainTest(environment.getKafka(), JWTPLAIN_LISTENER, true).doTest();
    }

    @Test
    @Order(9)
    public void doOAuthOverPlainIntrospectionTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPlain + IntrospectionValidationAuthzTest");
        new OAuthOverPlainTest(environment.getKafka(), INTROSPECTPLAIN_LISTENER, true).doTest();
    }

    @Test
    @Order(10)
    public void doOAuthOverPlainFloodTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPLain + FloodTest");
        new FloodTest(JWTPLAIN_LISTENER, true).doTest();
    }

    @Test
    @Order(11)
    public void doJwtFloodTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: JWT FloodTest");
        new FloodTest(JWT_LISTENER, false).doTest();
    }

    @Test
    @Order(12)
    public void doIntrospectionFloodTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: Introspection FloodTest");
        new FloodTest(INTROSPECT_LISTENER, false).doTest();
    }

    // This test has to be the last one - it changes the team-a-client, and team-b-client permissions in Keycloak
    @Test
    @Order(13)
    public void doRefreshTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: JwtValidationAuthzTest + RefreshGrants");
        new RefreshTest(environment.getKafka(), JWTREFRESH_LISTENER, false).doTest();
    }

    @Test
    @Order(14)
    public void doSingletonTest() throws Exception {
        logStart("KeycloakKRaftAuthorizationTest :: SingletonTest");
        new SingletonTest(environment.getKafka()).doSingletonTest(2);
    }
}
