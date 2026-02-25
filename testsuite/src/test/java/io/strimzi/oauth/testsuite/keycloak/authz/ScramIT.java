/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigScramSimple;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.assertTrueExtra;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for SCRAM authentication mechanism
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        authenticationType = AuthenticationType.NONE,
        setupAcls = true,
        scramUsers = {"alice:alice-secret", "admin:admin-secret"},
        kafkaProperties = {
            "sasl.enabled.mechanisms=SCRAM-SHA-512",
            "listener.security.protocol.map=PLAINTEXT:SASL_PLAINTEXT,BROKER1:PLAINTEXT,CONTROLLER:PLAINTEXT",
            "listener.name.plaintext.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"admin\" password=\"admin-secret\" ;",
            "principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder",
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
        }
    )
)
public class ScramIT {

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.SCRAM)
    @Tag(TestTags.AUTHENTICATION)
    public void testScramAuthenticatedSessions() throws Exception {
        // bobby:bobby-secret is defined in docker-compose.yaml in the PLAIN listener configuration (port 9100)
        String username = "bobby";
        String password = "bobby-secret";

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        Properties producerProps = buildProducerConfigScramSimple(env.getBootstrapServers(), username, password);
        try {
            produceMessage(producerProps, "KeycloakAuthorizationTest-multiSaslTest-scram", "The Message");
            fail("Message producer should have failed, but it didn't");
        } catch (ExecutionException e) {
            assertTrueExtra("Instance of authentication exception", e.getCause() instanceof AuthenticationException, e);
        }

        // alice:alice-secret (user 'alice' has been configured for SCRAM in 'docker/kafka/scripts/start.sh')
        username = "alice";
        password = "alice-secret";

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should succeed for KeycloakAuthorizationTest-multiSaslTest-scram.
        // User 'alice' was configured for SASL SCRAM in 'docker/kafka/scripts/start.sh'
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        producerProps = buildProducerConfigScramSimple(env.getBootstrapServers(), username, password);
        produceMessage(producerProps, "KeycloakAuthorizationTest-multiSaslTest-scram", "The Message");
        try {
            produceMessage(producerProps, "KeycloakAuthorizationTest-multiSaslTest-scram-denied", "The Message");
            fail("Message producer should have failed, but it didn't");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthorizationException.class, e.getCause(), "Instance of authorization exception");
        }

        // OAuth authentication using SCRAM password should fail
        try {
            KafkaClientsConfig.loginWithUsernamePasswordInBody(
                    URI.create(env.getTokenEndpointUri()),
                    username, password, "kafka-cli");

            fail("Message producer should have failed, but it didn't");
        } catch (HttpException e) {
            assertEquals(401, e.getStatus(), "Status 401");
        }
    }

}
