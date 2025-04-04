/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import io.strimzi.testsuite.oauth.mockoauth.jdk17.KeycloakAuthorizerTest;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * Some tests rely on <code>resources/simplelogger.properties</code> to be configured to log to the file <code>target/test.log</code>.
 * <p>
 * Log output is analyzed in the test to make sure the behaviour is as expected.
 */
public class MockOAuthJdk17Tests {

    @ClassRule
    public static TestContainersWatcher environment =
            initWatcher();

    private static TestContainersWatcher initWatcher() {
        TestContainersWatcher watcher = new TestContainersWatcher(new File("docker-compose.yml"));
        watcher.withServices("mockoauth", "kafka");
        watcher.waitingFor("mockoauth", Wait.forLogMessage(".*Succeeded in deploying verticle.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(180)))
                .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                        .withStartupTimeout(Duration.ofSeconds(300)));

        return watcher;
    }

    @Rule
    public TestRule logCollector = new TestContainersLogCollector(environment);

    private static final Logger log = LoggerFactory.getLogger(MockOAuthJdk17Tests.class);

    @BeforeClass
    public static void staticInit() throws IOException {
        KeycloakAuthorizerTest.staticInit();
    }

    @Test
    public void runTests() throws Exception {
        try {
            System.out.println("See log at: " + new File("target/test.log").getAbsolutePath());

            // Keycloak authorizer tests
            new KeycloakAuthorizerTest().doTests();

        } catch (Throwable e) {
            log.error("Exception has occurred: ", e);
            throw e;
        }
    }
}
