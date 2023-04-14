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
import io.strimzi.testsuite.oauth.authz.SingletonTest;
import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

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
public class KeycloakKRaftAuthorizationTests {

    @ClassRule
    public static TestContainersWatcher environment =
            new TestContainersWatcher(new File("docker-compose.yml"))
                    .withServices("keycloak", "kafka", "kafka-acls")

                    // ensure kafka has started
                    .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                            .withStartupTimeout(Duration.ofSeconds(60)));

                    // ensure ACLs for user 'alice' have been added
                    //   Moved into test code: waitForACLs()

    @Rule
    public TestRule logCollector = new TestContainersLogCollector(environment);

    private static final Logger log = LoggerFactory.getLogger(KeycloakKRaftAuthorizationTests.class);

    private static final String JWT_LISTENER = "kafka:9092";
    private static final String INTROSPECT_LISTENER = "kafka:9093";
    private static final String JWTPLAIN_LISTENER = "kafka:9094";
    private static final String INTROSPECTPLAIN_LISTENER = "kafka:9095";
    private static final String JWTREFRESH_LISTENER = "kafka:9096";


    @Test
    public void doTest() throws Exception {
        try {

            String kafkaContainer = environment.getContainerByServiceName("kafka_1").get().getContainerInfo().getName().substring(1);

            logStart("KeycloakKRaftAuthorizationTest :: ConfigurationTest");
            new ConfigurationTest(kafkaContainer).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: MetricsTest (part 1)");
            MetricsTest.doTest();

            // Before running the rest of the tests, ensure ACLs have been added to Kafka cluster
            waitForACLs();

            logStart("KeycloakKRaftAuthorizationTest :: MultiSaslTests");
            new MultiSaslTest(kafkaContainer).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: JwtValidationAuthzTest");
            new BasicTest(kafkaContainer, JWT_LISTENER, false).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: IntrospectionValidationAuthzTest");
            new BasicTest(kafkaContainer, INTROSPECT_LISTENER, false).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: MetricsTest (part 2)");
            MetricsTest.doTestValidationAndAuthorization();

            logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPlain + JwtValidationAuthzTest");
            new OAuthOverPlainTest(kafkaContainer, JWTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPlain + IntrospectionValidationAuthzTest");
            new OAuthOverPlainTest(kafkaContainer, INTROSPECTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: OAuthOverPLain + FloodTest");
            new FloodTest(JWTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: JWT FloodTest");
            new FloodTest(JWT_LISTENER, false).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: Introspection FloodTest");
            new FloodTest(INTROSPECT_LISTENER, false).doTest();

            // This test has to be the last one - it changes the team-a-client, and team-b-client permissions in Keycloak
            logStart("KeycloakKRaftAuthorizationTest :: JwtValidationAuthzTest + RefreshGrants");
            new RefreshTest(kafkaContainer, JWTREFRESH_LISTENER, false).doTest();

            logStart("KeycloakKRaftAuthorizationTest :: SingletonTest");
            new SingletonTest(kafkaContainer).doSingletonTest(2);

        } catch (Throwable e) {
            log.error("Keycloak Raft Authorization Test failed: ", e);
            throw e;
        }
    }
}
