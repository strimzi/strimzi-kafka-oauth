/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Tests for OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with SASL OAUTHBEARER mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with SASL PLAIN mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
public class KeycloakAuthenticationTest {

    @ClassRule
    public static TestContainersWatcher logAction = new TestContainersWatcher();

    @ClassRule
    public static DockerComposeContainer<?> environment =
            new DockerComposeContainer<>(new File("docker-compose.yml"))
                    .withLocalCompose(true)
                    .withEnv("KAFKA_DOCKER_IMAGE", System.getProperty("KAFKA_DOCKER_IMAGE"))
                    .withServices("keycloak", "zookeeper", "kafka")
                    .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    @ClassRule
    public static TestContainersLogCollector logCollector = new TestContainersLogCollector(environment);

    static final Logger log = LoggerFactory.getLogger(KeycloakAuthenticationTest.class);

    @Test
    public void doTest() throws Exception {
        try {
            String kafkaContainer = environment.getContainerByServiceName("kafka_1").get().getContainerInfo().getName().substring(1);

            logStart("KeycloakAuthenticationTest :: BasicTests");
            new BasicTests(kafkaContainer).doTests();

            logStart("KeycloakAuthenticationTest :: OAuthOverPlainTests");
            OAuthOverPlainTests.doTests();

            logStart("KeycloakAuthenticationTest :: AudienceTests");
            AudienceTests.doTests();

            logStart("KeycloakAuthenticationTest :: CustomCheckTests");
            CustomCheckTests.doTests();

            logStart("KeycloakAuthenticationTest :: GroupsExtractionTests");
            new GroupsExtractionTests(kafkaContainer).doTests();

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
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
        System.out.println();
    }
}
