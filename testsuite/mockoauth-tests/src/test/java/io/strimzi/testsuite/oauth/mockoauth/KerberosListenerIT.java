/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Tests for Kerberos listener authentication.
 * Verifies that Kafka can authenticate clients using Kerberos SASL mechanism.
 * <p>
 * NOTE: This test is currently disabled because:
 * 1. It was never executed in the original MockOAuthTests (no method called it)
 * 2. MockOAuthTestEnvironment doesn't provide a Kerberos container
 * 3. Requires special Kerberos infrastructure setup
 * <p>
 * To enable this test, you need to:
 * - Add Kerberos container support to MockOAuthTestEnvironment
 * - Or create a separate test environment for Kerberos tests
 * - Or provide the Kerberos container through a different mechanism
 */
@Disabled("Kerberos tests require special infrastructure - MockOAuthTestEnvironment doesn't provide Kerberos container")
public class KerberosListenerIT {

    private static final String TOPIC_NAME = "Kerberos-Test-Topic";
    private static final long CONSUMER_TIMEOUT = 10000L;
    private static final int MESSAGE_COUNT = 100;

    private final GenericContainer<?> kerberosContainer;

    public KerberosListenerIT(GenericContainer<?> kerberosContainer) {
        this.kerberosContainer = kerberosContainer;
    }

    @Test
    @DisplayName("Test Kerberos listener authentication and message flow")
    public void testKerberosListener() throws Exception {
        File keyTab = new File("target/kafka_client.keytab");
        kerberosContainer.copyFileFromContainer("/keytabs/kafka_client.keytab", keyTab.getAbsolutePath());
        Assertions.assertTrue(keyTab.exists());
        Assertions.assertTrue(keyTab.canRead());

        Properties props = new Properties();
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.kerberos.service.name", "kafka");
        props.put("sasl.jaas.config", "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab='" +
            keyTab.getAbsolutePath() + "' principal='kafka/client@KERBEROS';");
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9099");

        Admin admin = Admin.create(props);
        CreateTopicsResult result = admin.createTopics(Collections.singleton(new NewTopic(TOPIC_NAME, (short) 1, (short) 1)));
        try {
            result.all()
                .get();
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to create topic on Kerberos listener because of " + e.getMessage());
        }

        Properties producerProps = (Properties) props.clone();
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            try {
                producer.send(new ProducerRecord<>(TOPIC_NAME, String.format("message_%d", i)))
                    .get();
            } catch (ExecutionException e) {
                Assertions.fail("Failed to produce to Kerberos listener because of " + e.getCause());
                e.printStackTrace();
            }
        }

        Properties consumerProps = (Properties) props.clone();
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, String.format("kerberos_listener_test_%d", System.currentTimeMillis()));

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition tp = new TopicPartition(TOPIC_NAME, 0);
        consumer.assign(Collections.singleton(tp));
        consumer.seekToBeginning(consumer.assignment());
        long startTime = System.currentTimeMillis();

        int receiveCount = 0;

        while (System.currentTimeMillis() - startTime < CONSUMER_TIMEOUT) {
            ConsumerRecords<String, String> results = consumer.poll(Duration.ofMillis(300));
            for (ConsumerRecord<String, String> record : results) {
                if (record.value()
                    .startsWith("message_")) receiveCount++;
            }
        }
        Assertions.assertEquals(MESSAGE_COUNT, receiveCount, "Kerberos listener consumer should consume all messsages");

    }

}
