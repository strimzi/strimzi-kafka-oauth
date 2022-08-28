/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Tests for errors during OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with SASL OAUTHBEARER mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with SASL PLAIN mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
public class KeycloakErrorsTest {

    @ClassRule
    public static TestContainersWatcher environment =
            new TestContainersWatcher(new File("docker-compose.yml"))
                    .withServices("keycloak", "zookeeper", "kafka")
                    .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    static final Logger log = LoggerFactory.getLogger(KeycloakErrorsTest.class);

    @Test
    public void doTest() throws Exception {
        try {
            String kafkaContainer = environment.getContainerByServiceName("kafka_1").get().getContainerInfo().getName().substring(1);

            logStart("KeycloakErrorsTest :: ErrorReportingTests");
            new ErrorReportingTests(kafkaContainer).doTests();

        } catch (Throwable e) {
            log.error("Keycloak Errors Test failed: ", e);
            throw e;
        }
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }
}
