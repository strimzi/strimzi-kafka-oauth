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
import org.junit.Assert;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class KerberosListenerTest {

    private static final String TOPIC_NAME = "Kerberos-Test-Topic";
    private static final long CONSUMER_TIMEOUT = 10000L;
    private static final int MESSAGE_COUNT = 100;

    public void doTests() throws Exception {

        //File krb5 = new File("../docker/kerberos/krb5.conf");
        //Assert.assertTrue(krb5.exists());
        //Assert.assertTrue(krb5.canRead());

        File keyTab = new File("../docker/kerberos/keys/kafka_client.keytab");
        Assert.assertTrue(keyTab.exists());
        Assert.assertTrue(keyTab.canRead());

        //System.out.println(krb5.getAbsolutePath());
        //System.setProperty("java.security.krb5.conf", krb5.getAbsolutePath());
        System.out.println(System.getProperty("java.security.krb5.conf"));

        Properties props = new Properties();
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put("sasl.kerberos.service.name", "kafka");
        props.put("sasl.jaas.config", "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab='../docker/kerberos/keys/kafka_client.keytab' principal='kafka/client@KERBEROS';");
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9099");


        Admin admin = Admin.create(props);
        CreateTopicsResult result = admin.createTopics(Collections.singleton(new NewTopic(TOPIC_NAME, (short) 1, (short) 1)));
        try {
            result.all().get();
        } catch (Exception e) {
            Assert.fail("Failed to create topic on Kerberos listener because of " + e.getMessage());
            e.printStackTrace();
        }

        Properties producerProps = (Properties) props.clone();
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            try {
                producer.send(new ProducerRecord<>(TOPIC_NAME, String.format("message_%d", i))).get();
            } catch (ExecutionException e) {
                Assert.fail("Failed to produce to Kerberos listener because of " + e.getCause());
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
                if (record.value().startsWith("message_")) receiveCount++;
            }
        }
        Assert.assertEquals("Kerberos listener consumer should consume all messsages", MESSAGE_COUNT, receiveCount);

    }

}
