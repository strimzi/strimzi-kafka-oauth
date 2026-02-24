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
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
        String kafkaBootstrap, java.util.Map<String, String> oauthConfig,
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
        String kafkaBootstrap, java.util.Map<String, String> plainConfig,
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
            producer.send(new ProducerRecord<>(topic, message))
                .get();
            log.debug("Produced {}", message);
        }
    }

    /**
     * Consume a single message from the beginning of a topic partition and assert it matches the expected message.
     */
    public static void consumeAndAssert(Properties consumerProps, String topic, String expectedMessage) {
        ConsumerRecords<String, String> records = consume(consumerProps, topic);
        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals(expectedMessage, records.iterator()
                .next()
                .value(),
            "Is message text: '" + expectedMessage + "'");
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
            producer.send(new ProducerRecord<>(topic, "The Message"))
                .get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertEquals(SaslAuthenticationException.class, cause.getClass(),
                "Expected SaslAuthenticationException");
            messageChecker.accept(cause.getMessage());
        }
    }

    /**
     * Attempt to produce a message and expect a {@link TopicAuthorizationException}.
     */
    public static void produceFail(Properties producerProps, String topic, String message) throws Exception {
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, message)).get();
            Assertions.fail("Should not be able to send message");
        } catch (ExecutionException e) {
            Assertions.assertInstanceOf(TopicAuthorizationException.class, e.getCause(),
                "Should fail with TopicAuthorizationException");
        }
    }

    /**
     * Core consume method — creates consumer, seeks to beginning, polls, asserts >= 1 records.
     * Returns records so callers like consumeAndAssert can do additional checks.
     */
    public static ConsumerRecords<String, String> consume(Properties consumerProps, String topic) {
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));
            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).isEmpty()) {
                log.debug("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));
            ConsumerRecords<String, String> records = KafkaClientsConfig.poll(consumer);
            Assertions.assertTrue(records.count() >= 1, "Got message");
            return records;
        }
    }

    /**
     * Attempt to consume from a topic and expect a {@link TopicAuthorizationException}.
     */
    public static void consumeFail(Properties consumerProps, String topic) {
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));
            try {
                while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).isEmpty()) {
                    log.debug("No assignment yet for consumer");
                }
                consumer.seekToBeginning(singletonList(partition));
                consumer.poll(Duration.ofSeconds(1));
                Assertions.fail("Should fail with TopicAuthorizationException");
            } catch (TopicAuthorizationException expected) {
                // expected
            }
        }
    }

}
