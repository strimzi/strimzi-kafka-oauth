/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearerWithAccessToken;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlainSimple;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigScramSimple;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernamePassword;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;

/**
 * Tests for multiple SASL mechanisms on a single listener:
 * SCRAM-SHA-512 + OAUTHBEARER + PLAIN (OAuth-over-PLAIN).
 * <p>
 * Regular PLAIN (username/password without OAuth) is not tested because it conflicts
 * with OAuth-over-PLAIN on the same listener (both use SASL mechanism PLAIN with
 * different callback handlers).
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        authenticationType = AuthenticationType.NONE,
        scramUsers = {"alice:alice-secret"},
        kafkaProperties = {
            "sasl.enabled.mechanisms=OAUTHBEARER,PLAIN,SCRAM-SHA-512",
            "listener.security.protocol.map=PLAINTEXT:SASL_PLAINTEXT,BROKER1:PLAINTEXT,CONTROLLER:PLAINTEXT",
            "listener.name.plaintext.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + " oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/demo-ec/protocol/openid-connect/certs\""
                + " oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo-ec\""
                + " oauth.config.id=\"JWT\""
                + " oauth.fallback.username.claim=\"client_id\""
                + " oauth.fallback.username.prefix=\"service-account-\""
                + " unsecuredLoginStringClaim_sub=\"admin\" ;",
            "listener.name.plaintext.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler",
            "listener.name.plaintext.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required"
                + " oauth.token.endpoint.uri=\"http://keycloak:8080/realms/demo-ec/protocol/openid-connect/token\""
                + " oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/demo-ec/protocol/openid-connect/certs\""
                + " oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo-ec\" ;",
            "listener.name.plaintext.plain.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler",
            "listener.name.plaintext.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required ;",
            "principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder",
            "offsets.topic.replication.factor=1"
        }
    )
)
public class AuthMultiSaslIT {

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.MULTI_SASL)
    void testMultipleAuthOptionsNextToOAuthLibrary() throws Exception {
        String kafkaBootstrap = env.getBootstrapServers();

        // 1. alice via SCRAM -> succeed (alice is provisioned as a SCRAM user)
        Properties producerProps = buildProducerConfigScramSimple(kafkaBootstrap, "alice", "alice-secret");
        produceMessage(producerProps, "KeycloakAuthenticationTest-multiSaslTest-scram", "The Message");

        // 2. bobby via SCRAM -> fail (not registered for SCRAM)
        producerProps = buildProducerConfigScramSimple(kafkaBootstrap, "bobby", "bobby-secret");
        try {
            produceMessage(producerProps, "KeycloakAuthenticationTest-multiSaslTest-scram", "The Message");
            Assertions.fail("Should have failed - bobby is not registered for SCRAM");
        } catch (Exception e) {
            Assertions.assertInstanceOf(SaslAuthenticationException.class, e.getCause(),
                "Should fail with SaslAuthenticationException");
        }

        // 3. alice via OAUTHBEARER (access token) -> succeed
        String accessToken = loginWithUsernamePassword(
            URI.create("http://" + env.getKeycloakHostPort() + "/realms/demo-ec/protocol/openid-connect/token"),
            "alice", "alice-password", "kafka");
        producerProps = buildProducerConfigOAuthBearerWithAccessToken(kafkaBootstrap, accessToken);
        produceMessage(producerProps, "KeycloakAuthenticationTest-multiSaslTest-oauthbearer", "The Message");

        // 4. alice via OAuth-over-PLAIN ($accessToken:) -> succeed
        producerProps = buildProducerConfigPlainSimple(kafkaBootstrap, "alice", "$accessToken:" + accessToken);
        produceMessage(producerProps, "KeycloakAuthenticationTest-multiSaslTest-oauth-over-plain", "The Message");
    }

}
