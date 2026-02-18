/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.authz.Common.buildProducerConfigScram;
import static io.strimzi.oauth.testsuite.authz.Common.produceToTopic;
import static io.strimzi.oauth.testsuite.common.TestUtil.assertTrueExtra;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for SCRAM authentication mechanism
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScramIT {

    private static final String SCRAM_LISTENER = "localhost:9101";

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
    @DisplayName("Test SCRAM authenticated sessions")
    @Tag(TestTags.SCRAM)
    @Tag(TestTags.AUTHENTICATION)
    public void testScramAuthenticatedSessions() throws Exception {
        // bobby:bobby-secret is defined in docker-compose.yaml in the PLAIN listener configuration (port 9100)
        String username = "bobby";
        String password = "bobby-secret";

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'bobby' has not been configured for SCRAM in 'docker/kafka/scripts/start.sh'
        Properties producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
            fail("Should have failed");
        } catch (ExecutionException e) {
            assertTrueExtra("Instance of authentication exception", e.getCause() instanceof AuthenticationException, e);
        }

        // alice:alice-secret (user 'alice' has been configured for SCRAM in 'docker/kafka/scripts/start.sh')
        username = "alice";
        password = "alice-secret";

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should succeed for KeycloakAuthorizationTest-multiSaslTest-scram.
        // User 'alice' was configured for SASL SCRAM in 'docker/kafka/scripts/start.sh'
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram-denied", producerProps);
            fail("Should have failed");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthorizationException.class, e.getCause(), "Instance of authorization exception");
        }

        // OAuth authentication using SCRAM password should fail
        try {
            Common.loginWithUsernamePassword(
                    URI.create(Common.tokenEndpointUri()),
                    username, password, "kafka-cli");

            fail("Should have failed");
        } catch (HttpException e) {
            assertEquals(401, e.getStatus(), "Status 401");
        }
    }

    private static Properties producerConfigScram(String kafkaBootstrap, String username, String password) {
        Map<String, String> scramConfig = new HashMap<>();
        scramConfig.put("username", username);
        scramConfig.put("password", password);

        return buildProducerConfigScram(kafkaBootstrap, scramConfig);
    }
}
