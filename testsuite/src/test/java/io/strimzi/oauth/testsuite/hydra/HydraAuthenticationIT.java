/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.hydra;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.KafkaPreset;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildConsumerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.poll;

/**
 * Tests for OAuth authentication using Hydra
 *
 * This test assumes there are multiple listeners configured with OAUTHBEARER support, but each configured differently
 * - configured with different options, and even a different auth server host.
 *
 * There should be no authorization configured on the Kafka broker.
 */
@OAuthEnvironment(authServer = AuthServer.HYDRA, kafka = @KafkaConfig(preset = KafkaPreset.HYDRA))
public class HydraAuthenticationIT {

    private static final Logger log = LoggerFactory.getLogger(HydraAuthenticationIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Authentication with PKCS12 truststore")
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PKCS12)
    public void testWithPKCS() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
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

    @Test
    @DisplayName("Authentication with PEM truststore from file")
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PEM)
    public void testWithPemFromFile() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "docker/certificates/ca.crt");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "PEM");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PEM from file - opaque access token with introspect validation test");
            clientCredentialsWithJwtValidationTest("PEM from file - client credentials with JWT validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    @Test
    @DisplayName("Authentication with PEM truststore from string")
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PEM)
    public void testWithPemFromString() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_CERTIFICATES, new String(Files.readAllBytes(Paths.get("docker/certificates/ca.crt"))));
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

    private void opaqueAccessTokenWithIntrospectValidationTest(String title) throws Exception {
        final String kafkaBootstrap = "localhost:9092";
        final String hostPort = System.getProperty("hydra.host") + ":" + System.getProperty("hydra.port");

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

        String topic = "HydraAuthenticationTest-" + toCamelCase(title);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                log.debug("No assignment yet for consumer");
            }
            consumer.seekToBeginning(Collections.singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }
    }

    private void clientCredentialsWithJwtValidationTest(String title) throws Exception {
        final String kafkaBootstrap = "localhost:9093";
        final String hostPort = System.getProperty("hydra.jwt.host") + ":" + System.getProperty("hydra.jwt.port");
        final String tokenEndpointUri = "https://" + hostPort + "/oauth2/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        String topic = "HydraAuthenticationTest-" + toCamelCase(title);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                log.debug("No assignment yet for consumer");
            }
            consumer.seekToBeginning(Collections.singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }
    }

    private static void clearSystemProperties(Properties defaults) {
        Properties p = new ConfigProperties(defaults).resolveTo(new Properties());
        for (Object key: p.keySet()) {
            System.clearProperty(key.toString());
        }
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
