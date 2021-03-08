/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;

public class FloodTest extends Common {

    private static ArrayList<Thread> threads = new ArrayList<>();

    private static AtomicInteger startedCount;

    static int sendLimit = 1;


    FloodTest(String kafkaBootstrap, boolean oauthOverPlain) {
        super(kafkaBootstrap, oauthOverPlain);
    }

    public void doTest() throws IOException {
        clientCredentialsWithFloodTest();
    }


    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     *
     * It connects concurrently with multiple producers with different client IDs using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     * With KeycloakRBACAuthorizer configured, any mixup of the credentials between different clients will be caught as
     * AuthorizationException would be thrown trying to write to the topic if the user context was mismatched.
     *
     * @throws Exception
     */
    void clientCredentialsWithFloodTest() throws IOException {

        String clientPrefix = "kafka-producer-client-";

        if (!usePlain) {
            HashMap<String, String> tokens = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                String clientId = clientPrefix + i;
                String secret = clientId + "-secret";

                tokens.put(clientId, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                        clientId, secret, true, null, null).token());
            }
            this.tokens = tokens;
        }

        // We do 10 iterations - each time hitting the broker with 5 parallel requests
        for (int run = 0; run < 10; run++) {

            for (int i = 1; i <= 5; i++) {
                String clientId = clientPrefix + i;
                String secret = clientId + "-secret";
                String topic = "messages-" + i;

                addProducerThread(clientId, secret, topic);
            }

            // Start all threads
            startThreads();

            // Wait for all threads to finish
            joinThreads();

            // Check for errors
            checkExceptions();

            // Prepare for the next run
            clearThreads();
        }

        // Try write to the mismatched topic - we should get AuthorizationException
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

    public void addProducerThread(String clientId, String secret, String topic) {
        FloodProducer p = new FloodProducer(clientId, secret, topic);
        p.initProducer();
    }


    class FloodProducer extends Thread {

        final private String clientId;
        final private String secret;
        final private String topic;
        private Throwable error;

        Producer<String, String> producer;

        FloodProducer(String clientId, String secret, String topic) {

            this.clientId = clientId;
            this.secret = secret;
            this.topic = topic;
        }

        private void initProducer() {
            Properties props = buildProducerConfig(kafkaBootstrap, usePlain, clientId, secret);
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

}
