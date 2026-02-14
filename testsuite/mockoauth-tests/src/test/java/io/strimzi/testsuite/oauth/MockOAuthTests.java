/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import io.strimzi.testsuite.oauth.mockoauth.AuthorizationEndpointsTest;
import io.strimzi.testsuite.oauth.mockoauth.ClientAssertionAuthTest;
import io.strimzi.testsuite.oauth.mockoauth.ConnectTimeoutTests;
import io.strimzi.testsuite.oauth.mockoauth.JWKSKeyUseTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasClientConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasServerConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.JwtExtractTest;
import io.strimzi.testsuite.oauth.mockoauth.PasswordAuthAndPrincipalExtractionTest;
import io.strimzi.testsuite.oauth.mockoauth.RetriesTests;

import io.strimzi.testsuite.oauth.mockoauth.metrics.MetricsTest;
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

import java.io.File;

/**
 * Some tests rely on <code>resources/simplelogger.properties</code> to be configured to log to the file <code>target/test.log</code>.
 * <p>
 * Log output is analyzed in the test to make sure the behaviour is as expected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MockOAuthTests {

    private static final Logger log = LoggerFactory.getLogger(MockOAuthTests.class);

    private MockOAuthTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new MockOAuthTestEnvironment();
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
    public void doMetricsTest() throws Exception {
        logStart("MetricsTest :: Basic Metrics Tests");
        System.out.println("See log at: " + new File("target/test.log").getAbsolutePath());
        new MetricsTest().doTest();
    }

    @Test
    @Order(2)
    public void doJWKSKeyUseTest() throws Exception {
        logStart("JWKSKeyUseTest :: JWKS KeyUse Test");
        new JWKSKeyUseTest().doTest();
    }

    @Test
    @Order(3)
    public void doJwtExtractTest() throws Exception {
        logStart("JwtExtractTest :: JWT 'exp' attribute overflow Test");
        new JwtExtractTest().doTest();
    }

    @Test
    @Order(4)
    public void doJaasClientConfigTest() throws Exception {
        logStart("JaasClientConfigTest :: Client Configuration Tests");
        new JaasClientConfigTest().doTest();
    }

    @Test
    @Order(5)
    public void doAuthorizationEndpointsTest() throws Exception {
        logStart("AuthorizationEndpointTest :: Server Configuration Tests");
        new AuthorizationEndpointsTest().doTest();
    }

    @Test
    @Order(6)
    public void doJaasServerConfigTest() throws Exception {
        logStart("JaasServerConfigTest :: Server Configuration Tests");
        new JaasServerConfigTest().doTest();
    }

    @Test
    @Order(7)
    public void doPasswordAuthAndPrincipalExtractionTest() throws Exception {
        logStart("PasswordAuthAndPrincipalExtractionTest :: Password Grant  +  Fallback Username / Prefix Tests");
        new PasswordAuthAndPrincipalExtractionTest().doTest();
    }

    @Test
    @Order(8)
    public void doConnectTimeoutTests() throws Exception {
        logStart("ConnectTimeoutTests :: HTTP Timeout Tests");
        new ConnectTimeoutTests(environment.getKafka()).doTest();
    }

    @Test
    @Order(9)
    public void doRetriesTests() throws Exception {
        logStart("RetriesTests :: Authentication HTTP Retries Tests");
        new RetriesTests(environment.getKafka()).doTests();
    }

    @Test
    @Order(10)
    public void doClientAssertionAuthTest() throws Exception {
        logStart("ClientAssertionAuthTest :: Client Assertion Tests");
        new ClientAssertionAuthTest().doTest();
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }
}
