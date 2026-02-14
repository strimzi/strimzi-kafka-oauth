/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import org.testcontainers.containers.GenericContainer;

import java.util.List;

import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationTest {

    private final GenericContainer<?> kafkaContainer;

    public ConfigurationTest(GenericContainer<?> kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    public void doTest() {
        // get kafka log and make sure KeycloakRBACAuthorizer has been configured with expected settings
        List<String> lines = getContainerLogsForString(kafkaContainer, "Configured KeycloakRBACAuthorizer");
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
