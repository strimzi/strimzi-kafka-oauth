/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.HttpException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.Assert;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigScram;
import static io.strimzi.testsuite.oauth.authz.Common.produceToTopic;

@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public class ScramTest {

    private static final String SCRAM_LISTENER = "kafka:9101";

    private static Properties producerConfigScram(String kafkaBootstrap, String username, String password) {
        Map<String, String> scramConfig = new HashMap<>();
        scramConfig.put("username", username);
        scramConfig.put("password", password);

        return buildProducerConfigScram(kafkaBootstrap, scramConfig);
    }

    public void doTest() throws Exception {
        testScramAuthenticatedSessions();
    }

    void testScramAuthenticatedSessions() throws Exception {
        // bobby:bobby-secret is defined in docker-compose.yaml in the PLAIN listener configuration (port 9100)
        String username = "bobby";
        String password = "bobby-secret";

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'bobby' has not been configured for SCRAM in 'docker/kafka/scripts/start.sh'
        Properties producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
            Assert.fail("Should have failed");
        } catch (ExecutionException e) {
            Assert.assertTrue("Instance of authentication exception", e.getCause() instanceof AuthenticationException);
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
            Assert.fail("Should have failed");
        } catch (ExecutionException e) {
            Assert.assertTrue("Instance of authorization exception", e.getCause() instanceof AuthorizationException);
        }

        // OAuth authentication using SCRAM password should fail
        try {
            Common.loginWithUsernamePassword(
                    URI.create("http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token"),
                    username, password, "kafka-cli");

            Assert.fail("Should have failed");
        } catch (HttpException e) {
            Assert.assertEquals("Status 401", 401, e.getStatus());
        }
    }
}
