/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;

/**
 * This class uses multiple KafkaProducers, configured with different clientIds to concurrently produce
 * messages as quickly as possible.
 */
public class FloodProducer extends Thread {

    private static ArrayList<Thread> threads = new ArrayList<>();

    static private AtomicInteger startedCount;

    static int sendLimit = 1;

    final private String kafkaBootstrap;
    final private String clientId;
    final private String secret;
    final private String topic;
    private Throwable error;

    Producer<String, String> producer;


    FloodProducer(String kafkaBootstrap, String clientId, String secret, String topic) {
        this.kafkaBootstrap = kafkaBootstrap;
        this.clientId = clientId;
        this.secret = secret;
        this.topic = topic;
    }

    public static void clearThreads() {
        threads.clear();
    }

    public static void startThreads() {
        startedCount = new AtomicInteger(0);
        for (Thread t : threads) {
            t.start();
        }
    }

    public static void joinThreads() {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted - exiting ...");
            }
        }
    }

    public static void checkExceptions() {
        try {
            for (Thread t : threads) {
                ((FloodProducer) t).checkException();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Test failed due to: ", t);
        }
    }

    public static void addProducerThread(String kafkaBootstrap, String clientId, String secret, String topic) {
        FloodProducer p = new FloodProducer(kafkaBootstrap, clientId, secret, topic);
        p.initProducer();
    }

    private void initProducer() {
        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", clientId);
        plainConfig.put("password", secret);

        Properties props = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        producer = new KafkaProducer<>(props);
        setName("FloodProducer Runner Thread - " + threads.size());
        threads.add(this);
    }

    private void checkException() throws Throwable {
        if (error != null) {
            throw error;
        }
    }

    public void run() {
        int started = startedCount.addAndGet(1);

        try {

            while (started < threads.size()) {
                Thread.sleep(10);
                started = startedCount.get();
            }

            for (int i = 0; i < sendLimit; i++) {
                producer.send(new ProducerRecord<>(topic, "Message " + i))
                        .get();

                System.out.println("[" + clientId + "] Produced message to '" + topic + "': Message " + i);

                if (i < sendLimit - 1) {
                    Thread.sleep(2000);
                }
            }
        } catch (InterruptedException e) {
            error = new RuntimeException("Interrupted while sending!");
        } catch (ExecutionException e) {
            error = new RuntimeException("Failed to send message: ", e);
        } catch (Throwable t) {
            error = t;
        } finally {
            if (producer != null) {
                producer.close();
            }
        }
    }
}
