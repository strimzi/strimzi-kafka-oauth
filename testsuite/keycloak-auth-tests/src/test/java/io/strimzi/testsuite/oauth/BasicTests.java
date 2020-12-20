/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.testsuite.oauth.Common.buildConsumerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.Common.loginWithUsernameForRefreshToken;
import static java.util.Collections.singletonList;

public class BasicTests {

    static void doTests() throws Exception {
        clientCredentialsWithJwtECDSAValidation();
        clientCredentialsWithJwtRSAValidation();
        accessTokenWithIntrospection();
        refreshTokenWithIntrospection();
    }

    static void clientCredentialsWithJwtECDSAValidation() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsWithJwtECDSAValidationTest ====");

        final String kafkaBootstrap = "kafka:9092";
        final String hostPort = "keycloak:8080";
        final String realm = "demo-ec";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtECDSAValidationTest";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }

    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN, and the Keycloak realm
     * that uses the default RSA cryptography to sign tokens.
     *
     * It connects to the Kafka using the OAUTHBEARER mechanism
     *
     * @throws Exception
     */
    static void clientCredentialsWithJwtRSAValidation() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsWithJwtRSAValidation ====");

        final String kafkaBootstrap = "kafka:9096";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtRSAValidationTest";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }

    static void accessTokenWithIntrospection() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: accessTokenWithIntrospectionTest ====");

        final String kafkaBootstrap = "kafka:9093";
        final String hostPort = "keycloak:8080";
        final String realm = "demo";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);


        final String topic = "KeycloakAuthenticationTest-accessTokenWithIntrospectionTest";

        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }

    static void refreshTokenWithIntrospection() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: refreshTokenWithIntrospectionTest ====");

        final String kafkaBootstrap = "kafka:9093";
        final String hostPort = "keycloak:8080";
        final String realm = "demo";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-cli";
        final String username = "alice";
        final String password = "alice-password";

        // first, request access token using client id and secret
        String refreshToken = loginWithUsernameForRefreshToken(URI.create(tokenEndpointUri), username, password, clientId);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, clientId);
        oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, refreshToken);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);


        final String topic = "KeycloakAuthenticationTest-refreshTokenWithIntrospectionTest";

        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }
}
