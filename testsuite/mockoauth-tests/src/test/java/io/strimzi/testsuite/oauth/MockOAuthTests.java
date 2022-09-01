/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import io.strimzi.testsuite.oauth.metrics.MetricsTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasClientConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.PasswordAuthTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

public class MockOAuthTests {

    @ClassRule
    public static TestContainersWatcher environment =
            new TestContainersWatcher(new File("docker-compose.yml"))
                    .withServices("mockoauth", "kafka", "zookeeper")
                    .waitingFor("mockoauth", Wait.forLogMessage(".*Succeeded in deploying verticle.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)))
                    .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    @Rule
    public TestRule logCollector = new TestContainersLogCollector(environment);

    private static final Logger log = LoggerFactory.getLogger(MockOAuthTests.class);

    @Test
    public void runTests() throws Exception {
        try {
            System.setProperty("oauth.read.timeout.seconds", "600");

            logStart("MetricsTest :: Basic Metrics Tests");
            new MetricsTest().doTest();

            logStart("JaasClientConfigTest :: Client Configuration Tests");
            new JaasClientConfigTest().doTest();

            logStart("PasswordAuthTest :: Password Grant Tests");
            new PasswordAuthTest().doTest();

        } catch (Throwable e) {
            log.error("Exception has occured: ", e);
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
