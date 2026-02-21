/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlain;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigScram;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernamePassword;

/**
 * Tests for multiple SASL mechanisms on a single listener:
 * SCRAM-SHA-512 + OAUTHBEARER + PLAIN (OAuth-over-PLAIN).
 *
 * Regular PLAIN (username/password without OAuth) is not tested because it conflicts
 * with OAuth-over-PLAIN on the same listener (both use SASL mechanism PLAIN with
 * different callback handlers).
 */
@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(
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
    }))
public class AuthMultiSaslIT {

    private static final Logger log = LoggerFactory.getLogger(AuthMultiSaslIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Multi SASL mechanism test")
    @Tag(TestTags.MULTI_SASL)
    void doTests() throws Exception {
        String kafkaBootstrap = env.getBootstrapServers();

        // 1. alice via SCRAM -> succeed (alice is provisioned as a SCRAM user)
        Properties producerProps = producerConfigScram(kafkaBootstrap, "alice", "alice-secret");
        produceToTopic("KeycloakAuthenticationTest-multiSaslTest-scram", producerProps);

        // 2. bobby via SCRAM -> fail (not registered for SCRAM)
        producerProps = producerConfigScram(kafkaBootstrap, "bobby", "bobby-secret");
        try {
            produceToTopic("KeycloakAuthenticationTest-multiSaslTest-scram", producerProps);
            Assertions.fail("Should have failed - bobby is not registered for SCRAM");
        } catch (Exception ignored) {
        }

        // 3. alice via OAUTHBEARER (access token) -> succeed
        String accessToken = loginWithUsernamePassword(
                URI.create("http://" + env.getKeycloakHostPort() + "/realms/demo-ec/protocol/openid-connect/token"),
                "alice", "alice-password", "kafka");
        producerProps = producerConfigOAuthBearerAccessToken(kafkaBootstrap, accessToken);
        produceToTopic("KeycloakAuthenticationTest-multiSaslTest-oauthbearer", producerProps);

        // 4. alice via OAuth-over-PLAIN ($accessToken:) -> succeed
        producerProps = producerConfigPlain(kafkaBootstrap, "alice", "$accessToken:" + accessToken);
        produceToTopic("KeycloakAuthenticationTest-multiSaslTest-oauth-over-plain", producerProps);
    }

    private Properties producerConfigScram(String kafkaBootstrap, String username, String password) {
        Map<String, String> scramConfig = new HashMap<>();
        scramConfig.put("username", username);
        scramConfig.put("password", password);
        return buildProducerConfigScram(kafkaBootstrap, scramConfig);
    }

    private Properties producerConfigPlain(String kafkaBootstrap, String username, String password) {
        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", username);
        plainConfig.put("password", password);
        return buildProducerConfigPlain(kafkaBootstrap, plainConfig);
    }

    private Properties producerConfigOAuthBearerAccessToken(String kafkaBootstrap, String accessToken) {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, accessToken);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");
        return buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
    }

    private void produceToTopic(String topic, Properties config) throws Exception {
        try (Producer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }
    }
}
