/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for KeycloakRBACAuthorizer configuration verification
 */
@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(realm = "kafka-authz",
    setupAcls = true,
    oauthProperties = {
        "oauth.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
        "oauth.client.id=kafka",
        "oauth.client.secret=kafka-secret",
        "oauth.groups.claim=$.realm_access.roles",
        "oauth.fallback.username.claim=username",
        "unsecuredLoginStringClaim_sub=admin"
    },
    kafkaProperties = {
        "authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer",
        "strimzi.authorization.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
        "strimzi.authorization.client.id=kafka",
        "strimzi.authorization.client.secret=kafka-secret",
        "strimzi.authorization.kafka.cluster.name=my-cluster",
        "strimzi.authorization.delegate.to.kafka.acl=true",
        "strimzi.authorization.read.timeout.seconds=45",
        "strimzi.authorization.grants.refresh.pool.size=4",
        "strimzi.authorization.grants.refresh.period.seconds=10",
        "strimzi.authorization.http.retries=1",
        "strimzi.authorization.reuse.grants=true",
        "strimzi.authorization.enable.metrics=true",
        "super.users=User:admin;User:service-account-kafka"
    }))
public class ConfigurationIT {

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Verify KeycloakRBACAuthorizer configuration settings")
    @Tag(TestTags.CONFIGURATION)
    public void verifyAuthorizerConfiguration() {
        // get kafka log and make sure KeycloakRBACAuthorizer has been configured with expected settings
        List<String> lines = getContainerLogsForString(env.getKafka(), "Configured KeycloakRBACAuthorizer");
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
