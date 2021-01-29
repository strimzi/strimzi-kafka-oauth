/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.junit.Assert;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.loginWithUsernamePassword;

public class CustomCheckTests {

    static void doTests() throws Exception {
        customClaimCheckWithJwtTest();
        customClaimCheckWithIntrospectionTest();
    }

    private static void customClaimCheckWithJwtTest() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: customClaimCheckWithJwtTest ====");
        runTest("kafka:9098");
    }

    private static void customClaimCheckWithIntrospectionTest() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: customClaimCheckWithIntrospectionTest ====");
        runTest("kafka:9099");
    }

    private static void runTest(String kafkaBootstrap) throws Exception {

        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // logging in as 'team-b-client' should succeed - iss check, clientId check, aud check, resource_access check should all pass

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-customClaimCheckWithJwtTest";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");


        // logging in as 'bob' should fail - clientId check, aud check and resource_access check would all fail
        String token = loginWithUsernamePassword(URI.create(tokenEndpointUri), "bob", "bob-password", "kafka-cli");

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        producer = new KafkaProducer<>(producerProps);

        try {
            producer.send(new ProducerRecord<>(topic, "Bob's Message")).get();
            Assert.fail("Producing the message should have failed");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("instanceOf AuthenticationException", cause instanceof AuthenticationException);
            Assert.assertTrue("custom claim check failed", cause.toString().contains("Custom claim check failed"));
        }

    }

}
