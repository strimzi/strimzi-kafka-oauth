/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.KeycloakAuthTestEnvironment;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthenticationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.buildProducerConfigOAuthBearer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AudienceIT {

    private static final Logger log = LoggerFactory.getLogger(AudienceIT.class);

    private KeycloakAuthTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new KeycloakAuthTestEnvironment();
        environment.start();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @DisplayName("Client credentials with JWT audience validation")
    @Tag(TestTags.JWT)
    @Tag(TestTags.AUDIENCE)
    void clientCredentialsWithJwtAudience() throws Exception {
        final String kafkaBootstrap = "localhost:9094";
        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtAudienceTest";

            RecordMetadata result = producer.send(new ProducerRecord<>(topic, "message")).get();

            Assertions.assertTrue(result.hasOffset(), "Has offset");
        }

        log.debug("Produced The Message");

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtAudienceTest";

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "message2")).get();
            Assertions.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof AuthenticationException, "instanceOf AuthenticationException");
            Assertions.assertTrue(cause.toString().contains("audience not available"), "'audience not available' error mesage");
        }
    }

    @Test
    @DisplayName("Client credentials with introspection audience validation")
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.AUDIENCE)
    void clientCredentialsWithIntrospectionAudienceTest() throws Exception {
        final String kafkaBootstrap = "localhost:9095";
        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

            RecordMetadata result = producer.send(new ProducerRecord<>(topic, "message")).get();

            Assertions.assertTrue(result.hasOffset(), "Has offset");
        }

        log.debug("Produced The Message");

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "message2")).get();
            Assertions.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof AuthenticationException, "instanceOf AuthenticationException");
            Assertions.assertTrue(cause.toString().contains("Invalid audience"), "'Invalid audience' error message");
        }
    }
}
