/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static io.strimzi.oauth.testsuite.common.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for KeycloakRBACAuthorizer configuration verification
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConfigurationIT {

    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

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
    @DisplayName("Verify KeycloakRBACAuthorizer configuration settings")
    @Tag("configuration")
    public void verifyAuthorizerConfiguration() {
        // get kafka log and make sure KeycloakRBACAuthorizer has been configured with expected settings
        List<String> lines = getContainerLogsForString(environment.getKafka(), "Configured KeycloakRBACAuthorizer");
        assertTrue(lines.size() > 0, "Kafka log should contain string: 'KeycloakRBACAuthorizer'");

        String value = getLoggerAttribute(lines, "connectTimeoutSeconds");
        assertEquals("20", value, "'connectTimeoutSeconds' should be 20");

        value = getLoggerAttribute(lines, "readTimeoutSeconds");
        assertEquals("45", value, "'readTimeoutSeconds' should be 45");

        value = getLoggerAttribute(lines, "enableMetrics");
        assertEquals("true", value, "'enableMetrics' should be true");

        value = getLoggerAttribute(lines, "httpRetries");
        assertEquals("1", value, "'httpRetries' should be 1");

        value = getLoggerAttribute(lines, "reuseGrants");
        assertEquals("true", value, "'reuseGrants' should be true");
    }

    private static String getLoggerAttribute(List<String> lines, String name) {
        for (String line: lines) {
            if (line.contains(name)) {
                String[] keyVal = line.split(":");
                return keyVal[1].trim().split(" ")[0].trim();
            }
        }
        return null;
    }
}
