/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.testsuite.oauth.common.TestContainersLogCollector;
import io.strimzi.testsuite.oauth.common.TestContainersWatcher;
import io.strimzi.testsuite.oauth.mockoauth.AuthorizationEndpointsTest;
import io.strimzi.testsuite.oauth.mockoauth.ClientAssertionAuthTest;
import io.strimzi.testsuite.oauth.mockoauth.ConnectTimeoutTests;
import io.strimzi.testsuite.oauth.mockoauth.JWKSKeyUseTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasClientConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasServerConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.JwtExtractTest;
import io.strimzi.testsuite.oauth.mockoauth.PasswordAuthAndPrincipalExtractionTest;
import io.strimzi.testsuite.oauth.mockoauth.RetriesTests;
import io.strimzi.testsuite.oauth.mockoauth.KerberosListenerTest;

import io.strimzi.testsuite.oauth.mockoauth.metrics.MetricsTest;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Some tests rely on <code>resources/simplelogger.properties</code> to be configured to log to the file <code>target/test.log</code>.
 * <p>
 * Log output is analyzed in the test to make sure the behaviour is as expected.
 */
public class MockOAuthTests {

    static boolean includeKerberosTests = new Config(System.getProperties()).getValueAsBoolean("oauth.testsuite.test.kerberos", true);

    @ClassRule
    public static TestContainersWatcher environment =
            initWatcher();

    private static TestContainersWatcher initWatcher() {
        TestContainersWatcher watcher = new TestContainersWatcher(new File(includeKerberosTests ? "docker-compose-kerberos.yml" : "docker-compose.yml"));
        if (includeKerberosTests) {
            watcher.withServices("mockoauth", "kerberos", "kafka")
                    .waitingFor("kerberos", Wait.forLogMessage(".*commencing operation.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(180)));
        } else {
            watcher.withServices("mockoauth", "kafka");
        }
        watcher.waitingFor("mockoauth", Wait.forLogMessage(".*Succeeded in deploying verticle.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(180)))
                .waitingFor("kafka", Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                        .withStartupTimeout(Duration.ofSeconds(300)));

        return watcher;
    }

    @Rule
    public TestRule logCollector = new TestContainersLogCollector(environment);

    private static final Logger log = LoggerFactory.getLogger(MockOAuthTests.class);

    @Test
    public void runTests() throws Exception {
        try {
            String kafkaContainer = environment.getContainerByServiceName("kafka_1").get().getContainerInfo().getName().substring(1);
            System.out.println("See log at: " + new File("target/test.log").getAbsolutePath());

            // MetricsTest has to be the first as it relies on initial configuration and behaviour of mockoauth server
            //   JWKS endpoint is expected to return 404
            //   Subsequent tests can change that, but it takes some seconds for Kafka to retry fetching JWKS keys
            logStart("MetricsTest :: Basic Metrics Tests");
            new MetricsTest().doTest();

            logStart("JWKSKeyUseTest :: JWKS KeyUse Test");
            new JWKSKeyUseTest().doTest();

            logStart("JwtExtractTest :: JWT 'exp' attribute overflow Test");
            new JwtExtractTest().doTest();

            logStart("JaasClientConfigTest :: Client Configuration Tests");
            new JaasClientConfigTest().doTest();

            logStart("AuthorizationEndpointTest :: Server Configuration Tests");
            new AuthorizationEndpointsTest().doTest();

            logStart("JaasServerConfigTest :: Server Configuration Tests");
            new JaasServerConfigTest().doTest();

            logStart("PasswordAuthAndPrincipalExtractionTest :: Password Grant  +  Fallback Username / Prefix Tests");
            new PasswordAuthAndPrincipalExtractionTest().doTest();

            logStart("ConnectTimeoutTests :: HTTP Timeout Tests");
            new ConnectTimeoutTests(kafkaContainer).doTest();

            logStart("RetriesTests :: Authentication HTTP Retries Tests");
            new RetriesTests(kafkaContainer).doTests();

            logStart("ClientAssertionAuthTest :: Client Assertion Tests");
            new ClientAssertionAuthTest().doTest();

            if (includeKerberosTests) {
                String kerberosContainer = includeKerberosTests ? environment.getContainerByServiceName("kerberos_1").get().getContainerInfo().getName().substring(1) : null;
                logStart("KerberosTests :: Test authentication with Kerberos");
                new KerberosListenerTest(kerberosContainer).doTests();
            }

        } catch (Throwable e) {
            log.error("Exception has occurred: ", e);
            throw e;
        }
    }


    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }
}
