/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import io.strimzi.testsuite.oauth.common.TestUtil;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.Collection;

/**
 * Tests for OAuth authentication using Keycloak + Keycloak Authorization Services based authorization when Kafka runs in KRaft mode
 *
 * This test assumes there are multiple listeners configured with OAUTHBEARER or PLAIN support, but each configured differently
 * - configured with different options, or different realm.
 *
 * There is KeycloakAuthorizer configured on the Kafka broker.
 */
public class KeycloakRaftAuthorizationTests {

    @ClassRule
    public static TestContainersWatcher environment =
            new TestContainersWatcher(new File("docker-compose.yml"))
                    .withServices("keycloak", "kafka", "kafka-acls")

                    // ensure kafka has started
                    .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                            .withStartupTimeout(Duration.ofSeconds(60)));

                    // ensure ACLs for user 'alice' have been added
                    //   Moved into test code: waitForACLs()
                    //   Logging has changed, and it would require very verbose logging to possibly detect this from kafka logs

                    // ensure a grants fetch request to 'keycloak' has been performed by authorizer's grants refresh job
                    //   In KRaft mode the inter-broker connection doesn't seem to happen, so there are no OAuth authenticated sessions before starting the tests
                    //.waitingFor("kafka", Wait.forLogMessage(".*after: \\{\\}.*", 1)
                    //        .withStartupTimeout(Duration.ofSeconds(210)));

    @Rule
    public TestRule logCollector = new TestContainersLogCollector(environment);

    private static final Logger log = LoggerFactory.getLogger(KeycloakRaftAuthorizationTests.class);

    private static final String JWT_LISTENER = "kafka:9092";
    private static final String INTROSPECT_LISTENER = "kafka:9093";
    private static final String JWTPLAIN_LISTENER = "kafka:9094";
    private static final String INTROSPECTPLAIN_LISTENER = "kafka:9095";
    private static final String JWTREFRESH_LISTENER = "kafka:9096";

    private static final String PLAIN_LISTENER = "kafka:9100";

    @Test
    public void doTest() throws Exception {
        try {
            String kafkaContainer = environment.getContainerByServiceName("kafka_1").get().getContainerInfo().getName().substring(1);

            logStart("KeycloakRaftAuthorizationTest :: ConfigurationTest");
            new ConfigurationTest(kafkaContainer).doTest();

            logStart("KeycloakRaftAuthorizationTest :: MetricsTest");
            MetricsTest.doTest();

            // Ensure ACLs have been added to Kafka cluster
            waitForACLs();

            logStart("KeycloakRaftAuthorizationTest :: MultiSaslTests");
            new MultiSaslTest(kafkaContainer).doTest();

            logStart("KeycloakRaftAuthorizationTest :: JwtValidationAuthzTest");
            new BasicTest(JWT_LISTENER, false).doTest();

            logStart("KeycloakRaftAuthorizationTest :: IntrospectionValidationAuthzTest");
            new BasicTest(INTROSPECT_LISTENER, false).doTest();

            logStart("KeycloakRaftAuthorizationTest :: OAuthOverPlain + JwtValidationAuthzTest");
            new OAuthOverPlainTest(JWTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakRaftAuthorizationTest :: OAuthOverPlain + IntrospectionValidationAuthzTest");
            new OAuthOverPlainTest(INTROSPECTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakRaftAuthorizationTest :: OAuthOverPLain + FloodTest");
            new FloodTest(kafkaContainer, JWTPLAIN_LISTENER, true).doTest();

            logStart("KeycloakRaftAuthorizationTest :: JWT FloodTest");
            new FloodTest(kafkaContainer, JWT_LISTENER, false).doTest();

            logStart("KeycloakRaftAuthorizationTest :: Introspection FloodTest");
            new FloodTest(kafkaContainer, INTROSPECT_LISTENER, false).doTest();

            // This test has to be the last one - it changes the team-a-client, and team-b-client permissions in Keycloak
            logStart("KeycloakRaftAuthorizationTest :: JwtValidationAuthzTest + RefreshGrants");
            new RefreshTest(JWTREFRESH_LISTENER, false).doTest();

        } catch (Throwable e) {
            log.error("Keycloak Raft Authorization Test failed: ", e);
            throw e;
        }
    }

    private void waitForACLs() throws Exception {

        // Create admin client using user `admin:admin-password` over PLAIN listener (port 9100)
        AdminClient adminClient = Common.buildAdminClientForPlain(PLAIN_LISTENER, "admin");

        TestUtil.waitForCondition(() -> {
            try {
                Collection<AclBinding> result = adminClient.describeAcls(new AclBindingFilter(ResourcePatternFilter.ANY,
                        new AccessControlEntryFilter("User:alice", null, AclOperation.IDEMPOTENT_WRITE, AclPermissionType.ALLOW))).values().get();
                for (AclBinding acl : result) {
                    if (AclOperation.IDEMPOTENT_WRITE.equals(acl.entry().operation())) {
                        return true;
                    }
                }
                return false;

            } catch (Exception e) {
                throw new RuntimeException("ACLs for User:alice could not be retrieved: ", e);
            }
        }, 500, 210);
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }
}
