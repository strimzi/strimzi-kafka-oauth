/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.testsuite.oauth.auth.Common.buildConsumerConfigPlain;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.auth.Common.poll;
import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;
import static java.util.Collections.singletonList;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OAuthOverPlainIT {

    private static final Logger log = LoggerFactory.getLogger(OAuthOverPlainIT.class);
    private static final String BROKER_KEYCLOAK_HOST = "keycloak:8080";

    private KeycloakAuthTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new KeycloakAuthTestEnvironment();
        environment.start();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Client credentials over PLAIN with JWT")
    @Tag("plain")
    @Tag("jwt")
    void clientCredentialsOverPlainWithJwt() throws Exception {
        final String kafkaBootstrap = "localhost:9096";
        final String hostPort = "localhost:8080";

        // For metrics
        final String realm = "kafka-authz";
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithJwt";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        log.debug("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwt >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwt > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(2)
    @DisplayName("Client credentials over PLAIN with introspection")
    @Tag("plain")
    @Tag("introspection")
    void clientCredentialsOverPlainWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9097";

        final String hostPort = "localhost:8080";
        final String realm = "kafka-authz";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithIntrospection";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        log.debug("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for introspect >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for introspect > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(3)
    @DisplayName("Access token over PLAIN with introspection")
    @Tag("plain")
    @Tag("introspection")
    void accessTokenOverPlainWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9097";
        final String hostPort = "localhost:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "team-a-client", "team-a-client-secret", true, null, null, true);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "service-account-team-a-client");
        plainConfig.put("password", "$accessToken:" + info.token());

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-accessTokenOverPlainWithIntrospection";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        log.debug("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 (on top of the previous test)
        Assertions.assertTrue(value != null && value.intValue() >= 8, "strimzi_oauth_validation_requests_count for introspect >= 8");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(4)
    @DisplayName("Client credentials over PLAIN with flood test")
    @Tag("plain")
    @Tag("performance")
    void clientCredentialsOverPlainWithFloodTest() {
        final String kafkaBootstrap = "localhost:9102";

        String clientPrefix = "kafka-producer-client-";

        // We do 5 iterations - each time hitting the broker with 10 parallel requests
        for (int run = 0; run < 5; run++) {

            for (int i = 1; i <= 10; i++) {
                String clientId = clientPrefix + i;
                String secret = clientId + "-secret";
                String topic = "messages-" + i;

                FloodProducer.addProducerThread(kafkaBootstrap, clientId, secret, topic);
            }

            // Start all threads
            FloodProducer.startThreads();

            // Wait for all threads to finish
            FloodProducer.joinThreads();

            // Check for errors
            FloodProducer.checkExceptions();

            // Prepare for the next run
            FloodProducer.clearThreads();
        }
    }

    @Test
    @Order(5)
    @DisplayName("Access token over PLAIN with client credentials disabled")
    @Tag("plain")
    void accessTokenOverPlainWithClientCredentialsDisabled() throws Exception {
        final String kafkaBootstrap = "localhost:9103";
        final String hostPort = "localhost:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "team-a-client", "team-a-client-secret", true, null, null, true);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "service-account-team-a-client");
        // we use no prefix ("$accessToken:") because access-token-only mode is in effect
        plainConfig.put("password", info.token());

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-accessTokenOverPlainWithClientCredentialsDisabled";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        log.debug("Produced The Message");

        Properties consumerProps = buildConsumerConfigPlain(kafkaBootstrap, plainConfig);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }
        consumer.seekToBeginning(singletonList(partition));

        ConsumerRecords<String, String> records = poll(consumer);

        Assertions.assertEquals(1, records.count(), "Got message");
        Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwks > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener
        // Validation did not require the broker authenticating in client's name, because the token was passed
        Assertions.assertEquals(0, value.intValue(), "strimzi_oauth_http_requests_count for plain == 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertEquals(0.0, value.doubleValue(), 0.0, "strimzi_oauth_http_requests_totaltimems for plain == 0.0");
    }
}
