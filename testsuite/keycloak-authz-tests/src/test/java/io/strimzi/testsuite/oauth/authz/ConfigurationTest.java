/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import org.junit.Assert;

import java.util.List;

import static io.strimzi.testsuite.oauth.common.TestUtil.getKafkaLogsForString;

public class ConfigurationTest {

    public static void doTest() {
        // get kafka log and make sure KeycloakRBACAuthorizer has been configured with expected settings
        List<String> lines = getKafkaLogsForString("Configured KeycloakRBACAuthorizer");
        Assert.assertTrue("Kafka log should contain string: 'KeycloakRBACAuthorizer'", lines.size() > 0);

        String value = getLoggerAttribute(lines, "connectTimeoutSeconds");
        Assert.assertEquals("'connectTimeoutSeconds' should be 20", "20", value);

        value = getLoggerAttribute(lines, "readTimeoutSeconds");
        Assert.assertEquals("'readTimeoutSeconds' should be 45", "45", value);
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
