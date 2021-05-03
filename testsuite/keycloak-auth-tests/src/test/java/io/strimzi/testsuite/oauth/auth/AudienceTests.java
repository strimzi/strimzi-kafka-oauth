/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthenticationException;
import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;

public class AudienceTests {

    static void doTests() throws Exception {
        clientCredentialsWithJwtAudience();
        clientCredentialsWithIntrospectionAudienceTest();
    }

    static void clientCredentialsWithJwtAudience() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsWithJwtAudienceTest ====");

        final String kafkaBootstrap = "kafka:9094";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtAudienceTest";

        RecordMetadata result = producer.send(new ProducerRecord<>(topic, "message")).get();

        Assert.assertTrue("Has offset", result.hasOffset());
        producer.close();

        System.out.println("Produced The Message");


        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        producer = new KafkaProducer<>(producerProps);
        try {
            producer.send(new ProducerRecord<>(topic, "message2")).get();
            Assert.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("instanceOf AuthenticationException", cause instanceof AuthenticationException);
            Assert.assertTrue("'audience not available' error mesage", cause.toString().contains("audience not available"));
        }
    }

    static void clientCredentialsWithIntrospectionAudienceTest() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsWithIntrospectionAudienceTest ====");

        final String kafkaBootstrap = "kafka:9095";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

        RecordMetadata result = producer.send(new ProducerRecord<>(topic, "message")).get();

        Assert.assertTrue("Has offset", result.hasOffset());
        producer.close();

        System.out.println("Produced The Message");


        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        producer = new KafkaProducer<>(producerProps);
        try {
            producer.send(new ProducerRecord<>(topic, "message2")).get();
            Assert.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("instanceOf AuthenticationException", cause instanceof AuthenticationException);
            Assert.assertTrue("'Invalid audience' error message", cause.toString().contains("Invalid audience"));
        }
    }
}
