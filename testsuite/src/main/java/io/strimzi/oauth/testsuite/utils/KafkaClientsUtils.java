/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.utils;

import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.singletonList;

/**
 * Helper methods for common Kafka produce/consume test patterns.
 */
public class KafkaClientsUtils {

    private static final Logger log = LoggerFactory.getLogger(KafkaClientsUtils.class);

    /**
     * Produce one message using OAuthBearer authentication, then consume it and assert it matches.
     */
    public static void produceAndConsumeOAuthBearer(
            String kafkaBootstrap, Map<String, String> oauthConfig,
            String topic, String message) throws Exception {

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, message);

        Properties consumerProps = KafkaClientsConfig.buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        consumeAndAssert(consumerProps, topic, message);
    }

    /**
     * Produce one message using PLAIN authentication, then consume it and assert it matches.
     */
    public static void produceAndConsumePlain(
            String kafkaBootstrap, Map<String, String> plainConfig,
            String topic, String message) throws Exception {

        Properties producerProps = KafkaClientsConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        produceMessage(producerProps, topic, message);

        Properties consumerProps = KafkaClientsConfig.buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
        consumeAndAssert(consumerProps, topic, message);
    }

    /**
     * Produce a single message using already-built producer properties.
     */
    public static void produceMessage(Properties producerProps, String topic, String message) throws Exception {
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, message)).get();
            log.debug("Produced {}", message);
        }
    }

    /**
     * Consume a single message from the beginning of a topic partition and assert it matches the expected message.
     */
    public static void consumeAndAssert(Properties consumerProps, String topic, String expectedMessage) {
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                log.debug("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = KafkaClientsConfig.poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals(expectedMessage, records.iterator().next().value(),
                    "Is message text: '" + expectedMessage + "'");
        }
    }

    /**
     * Attempt to produce a message and expect a {@link SaslAuthenticationException}.
     * The commonChecks (asserting the cause is SaslAuthenticationException) are done automatically.
     * The provided messageChecker receives the cause's message for additional assertions.
     */
    public static void expectSaslAuthFailure(
            Properties producerProps, String topic,
            java.util.function.Consumer<String> messageChecker) throws Exception {

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertEquals(SaslAuthenticationException.class, cause.getClass(),
                    "Expected SaslAuthenticationException");
            messageChecker.accept(cause.getMessage());
        }
    }
}
