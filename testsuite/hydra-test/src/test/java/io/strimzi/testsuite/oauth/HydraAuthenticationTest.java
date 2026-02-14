/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.common.TestUtil.logStart;

/**
 * Tests for OAuth authentication using Hydra
 *
 * This test assumes there are multiple listeners configured with OAUTHBEARER support, but each configured differently
 * - configured with different options, and even a different auth server host.
 *
 * There should be no authorization configured on the Kafka broker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HydraAuthenticationTest {

    private static final Logger log = LoggerFactory.getLogger(HydraAuthenticationTest.class);

    private HydraTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new HydraTestEnvironment();
        environment.start();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    public void doTest() throws Exception {
        logStart("========    Hydra Authentication Tests");
        testWithPKCS();
        testWithPemFromFile();
        testWithPemFromString();
    }

    private void testWithPKCS() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PKCS12 - opaque access token with introspect validation test");
            clientCredentialsWithJwtValidationTest("PKCS12 - client credentials with JWT validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    public void testWithPemFromFile() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/certificates/ca.crt");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "PEM");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PEM from file - opaque access token with introspect validation test");
            clientCredentialsWithJwtValidationTest("PEM from file - client credentials with JWT validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    public void testWithPemFromString() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_CERTIFICATES, new String(Files.readAllBytes(Paths.get("../docker/certificates/ca.crt"))));
        //defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, null);
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "PEM");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PEM from string - opaque access token with introspect validation test");
            clientCredentialsWithJwtValidationTest("PEM from string - client credentials with JWT validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    public void opaqueAccessTokenWithIntrospectValidationTest(String title) throws Exception {
        System.out.println("    ====    " + title);

        final String kafkaBootstrap = "localhost:9092";
        final String hostPort = "localhost:4444";

        final String tokenEndpointUri = "https://" + hostPort + "/oauth2/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = OAuthAuthenticator.loginWithClientSecret(URI.create(tokenEndpointUri),
                ConfigUtil.createSSLFactory(new ClientConfig()),
                null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        String topic = "HydraAuthenticationTest-" + toCamelCase(title);
        producer.send(new ProducerRecord<>(topic, "The Message")).get();


        producer.close();

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(Collections.singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        consumer.close();

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
    }

    public void clientCredentialsWithJwtValidationTest(String title) throws Exception {
        System.out.println("    ====    " + title);

        final String kafkaBootstrap = "localhost:9093";
        final String hostPort = "localhost:4454";
        final String tokenEndpointUri = "https://" + hostPort + "/oauth2/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        String topic = "HydraAuthenticationTest-" + toCamelCase(title);
        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        log.debug("Produced The Message");

        producer.close();

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(Collections.singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        consumer.close();

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
    }


    private static String getJaasConfigOptionsString(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent: options.entrySet()) {
            sb.append(" ").append(ent.getKey()).append("=\"").append(ent.getValue()).append("\"");
        }
        return sb.toString();
    }

    private static Properties buildProducerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        return p;
    }

    private static Properties buildConsumerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return p;
    }

    private static Properties buildCommonConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        String configOptions = getJaasConfigOptionsString(oauthConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " + configOptions + " ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        return p;
    }

    private static void clearSystemProperties(Properties defaults) {
        Properties p = new ConfigProperties(defaults).resolveTo(new Properties());
        for (Object key: p.keySet()) {
            System.clearProperty(key.toString());
        }
    }

    static <K, V> ConsumerRecords<K, V> poll(Consumer<K, V> consumer) {
        ConsumerRecords<K, V> result = consumer.poll(Duration.ofSeconds(5));
        if (result.isEmpty()) {
            log.warn("No result after 5 seconds. Repeating ...");
            result = consumer.poll(Duration.ofSeconds(5));
        }
        return result;
    }

    static String toCamelCase(String title) {
        String[] words = title.split("[\\s]+");
        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].toLowerCase(Locale.ENGLISH);
            if (i > 0) {
                words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
            }
        }
        return String.join("", words);
    }
}
