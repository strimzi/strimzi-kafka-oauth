/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import io.strimzi.testsuite.oauth.mockoauth.jdk17.KeycloakAuthorizerTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Some tests rely on <code>resources/simplelogger.properties</code> to be configured to log to the file <code>target/test.log</code>.
 * <p>
 * Log output is analyzed in the test to make sure the behaviour is as expected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MockOAuthJdk17Tests {

    private MockOAuthJdk17TestEnvironment env;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() -> env != null ? env.getContainers() : null);

    private static final Logger log = LoggerFactory.getLogger(MockOAuthJdk17Tests.class);

    @BeforeAll
    void setUp() throws IOException {
        env = new MockOAuthJdk17TestEnvironment();
        env.start();
        KeycloakAuthorizerTest.staticInit();
    }

    @AfterAll
    void tearDown() {
        if (env != null) {
            env.stop();
        }
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
