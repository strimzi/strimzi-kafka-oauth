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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.loginWithUsernamePassword;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CustomCheckIT {

    private static final Logger log = LoggerFactory.getLogger(CustomCheckIT.class);

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
    @DisplayName("Custom claim check with JWT validation")
    @Tag(TestTags.JWT)
    @Tag(TestTags.CUSTOM_CHECK)
    void customClaimCheckWithJwtTest() throws Exception {
        runTest("localhost:9098");
    }

    @Test
    @DisplayName("Custom claim check with introspection validation")
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.CUSTOM_CHECK)
    void customClaimCheckWithIntrospectionTest() throws Exception {
        runTest("localhost:9099");
    }

    private void runTest(String kafkaBootstrap) throws Exception {

        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // logging in as 'team-b-client' should succeed - iss check, clientId check, aud check, resource_access check should all pass

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-customClaimCheckWithJwtTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        // logging in as 'bob' should fail - clientId check, aud check and resource_access check would all fail
        String token = loginWithUsernamePassword(URI.create(tokenEndpointUri), "bob", "bob-password", "kafka-cli");

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "Bob's Message")).get();
            Assertions.fail("Producing the message should have failed");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof AuthenticationException, "instanceOf AuthenticationException");
            Assertions.assertTrue(cause.toString().contains("Custom claim check failed"), "custom claim check failed");
        }

    }

}
