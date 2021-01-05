/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import io.strimzi.kafka.oauth.common.HttpUtil;
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
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64encode;

@RunWith(Arquillian.class)
public class KeycloakRefreshTokenWithJwtValidationTest {

    private static final String HOST = "keycloak";
    private static final String REALM = "demo";

    private static final String CLIENT_ID = "kafka";
    private static final String CLIENT_SECRET = null;

    private static final String USERNAME = "alice";
    private static final String PASSWORD = "alice-password";

    @Test
    public void doTest() throws Exception {
        System.out.println("==== KeycloakRefreshTokenWithJwtValidationTest ====");

        final String topic = "KeycloakRefreshTokenWithJwtValidationTest";
        final String tokenEndpointUri = "http://" + HOST + ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

        String refreshToken = loginWithUsernamePasswordForRefreshToken(URI.create(tokenEndpointUri), CLIENT_ID, CLIENT_SECRET, USERNAME, PASSWORD);

        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        defaults.setProperty(ClientConfig.OAUTH_CLIENT_ID, CLIENT_ID);
        //defaults.setProperty(ClientConfig.OAUTH_CLIENT_SECRET, CLIENT_SECRET);
        defaults.setProperty(ClientConfig.OAUTH_REFRESH_TOKEN, refreshToken);
        defaults.setProperty(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties producerProps = buildProducerConfig();
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        Properties consumerProps = buildConsumerConfig();
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Arrays.asList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(Arrays.asList(partition));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }


    private static Properties buildProducerConfig() {
        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        return p;
    }


    private static Properties buildConsumerConfig() {
        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        return p;
    }

    private String loginWithUsernamePasswordForRefreshToken(URI tokenEndpointUri, String clientId, String clientSecret, String username, String password) throws IOException {

        String authorization = clientSecret != null ? ("Basic " + base64encode(clientId + ':' + clientSecret)) : null;
        StringBuilder body = new StringBuilder("grant_type=password&username=" + username + "&password=" + password + "&client_id=" + clientId);
        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                authorization,
                "application/x-www-form-urlencoded",
                body.toString(),
                JsonNode.class);

        JsonNode token = result.get("refresh_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no refresh_token - " + result);
        }
        return token.asText();
    }
}
