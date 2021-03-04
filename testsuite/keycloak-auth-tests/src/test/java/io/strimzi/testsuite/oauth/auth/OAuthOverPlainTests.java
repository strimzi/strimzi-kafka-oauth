/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

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
import static io.strimzi.testsuite.oauth.auth.Common.buildConsumerConfigPlain;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;
import static java.util.Collections.singletonList;

public class OAuthOverPlainTests {

    static void doTests() throws Exception {
        clientCredentialsOverPlainWithJwt();
        clientCredentialsOverPlainWithIntrospection();
        accessTokenOverPlainWithIntrospection();
    }

    static void clientCredentialsOverPlainWithIntrospection() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsOverPlainWithIntrospection ====");

        final String kafkaBootstrap = "kafka:9097";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithIntrospection";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
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

    static void accessTokenOverPlainWithIntrospection() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: accessTokenOverPlainWithIntrospection ====");

        final String kafkaBootstrap = "kafka:9097";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "team-a-client", "team-a-client-secret", true, null, null);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "service-account-team-a-client");
        plainConfig.put("password", "$accessToken:" + info.token());

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-accessTokenOverPlainWithIntrospection";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
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
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     *
     * It connects to the Kafka using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     *
     * @throws Exception
     */
    static void clientCredentialsOverPlainWithJwt() throws Exception {

        System.out.println("==== KeycloakAuthenticationTest :: clientCredentialsOverPlainWithJwt ====");

        final String kafkaBootstrap = "kafka:9096";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithJwt";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
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
