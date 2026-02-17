/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestMetrics;
import io.strimzi.oauth.testsuite.common.metrics.TestMetricsReporter;
import io.strimzi.oauth.testsuite.environment.KeycloakAuthTestEnvironment;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.TokenIntrospection.introspectAccessToken;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.buildConsumerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.loginWithUsernameForRefreshToken;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.loginWithUsernamePassword;
import static io.strimzi.oauth.testsuite.utils.KafkaClientConfig.poll;
import static io.strimzi.oauth.testsuite.common.TestMetrics.getPrometheusMetrics;
import static io.strimzi.oauth.testsuite.common.TestUtil.getContainerLogsForString;
import static java.util.Collections.singletonList;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BasicIT {

    private static final Logger log = LoggerFactory.getLogger(BasicIT.class);

    // Keycloak host as seen by the broker (used in Prometheus metrics labels)
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
    @DisplayName("OAuth metrics config integration test")
    @Tag("metrics")
    void oauthMetricsConfigIntegration() {
        // Test that MetricReporter config works as expected
        // Get kafka log and make sure the TestMetricReporter was initialised exactly twice
        List<String> lines = getContainerLogsForString(environment.getKafka(), "TestMetricsReporter no. ");
        Assertions.assertEquals(1, lines.size(), "Kafka log should contain: \"TestMetricsReporter no. \" exactly once");
        Assertions.assertTrue(lines.get(0).contains("TestMetricsReporter no. 1 "), "Contains \"TestMetricsReporter no. 1\"");

        // Ensure the configuration was applied as expected
        lines = getContainerLogsForString(environment.getKafka(), "Creating Metrics:");
        String line = lines.get(1);
        Assertions.assertTrue(line.contains("samples: 3"), "samples: 3");
        Assertions.assertTrue(line.contains("recordingLevel: DEBUG"), "recordingLevel: DEBUG");
        Assertions.assertTrue(line.contains("timeWindowMs: 15000"), "timeWindowMs: 15000");

        line = lines.get(2);
        Assertions.assertTrue(line.contains("test.label=testvalue"), "test.label=testvalue");
        Assertions.assertTrue(line.contains("_namespace=strimzi.oauth"), "_namespace=strimzi.oauth");
        Assertions.assertTrue(line.contains("kafka.node.id=1"), "kafka.node.id=1");

        line = lines.get(3);
        Assertions.assertTrue(line.contains(TestMetricsReporter.class.getName()), TestMetricsReporter.class.getName());
        Assertions.assertTrue(line.contains("org.apache.kafka.common.metrics.JmxReporter"), "org.apache.kafka.common.metrics.JmxReporter");
    }

    @Test
    @DisplayName("OAuth metrics client authentication test")
    @Tag("metrics")
    void oauthMetricsClientAuth() throws Exception {
        final String realm = "demo";
        final String tokenPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // Inter-broker communication uses INTROSPECT listener
        // We use that listener to test client authentication metrics. There are 2 inter-broker client connections established.
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));

        // Request for token from login callback handler
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_count", "context", "INTROSPECT", "kind", "client-auth", "outcome", "success");
        Assertions.assertEquals(2, value.intValue(), "strimzi_oauth_authentication_requests_count for client-auth == 2");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_totaltimems", "context", "INTROSPECT", "kind", "client-auth", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_authentication_requests_totaltimems for client-auth > 0.0");

        // Authentication to keycloak to exchange clientId + secret for an access token during login callback handler call
        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECT", "kind", "client-auth", "host", BROKER_KEYCLOAK_HOST, "path", tokenPath, "outcome", "success");
        Assertions.assertEquals(2, value.intValue(), "strimzi_oauth_http_requests_count for client-auth == 2");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECT", "kind", "client-auth", "host", BROKER_KEYCLOAK_HOST, "path", tokenPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for client-auth > 0.0");
    }

    @Test
    @DisplayName("Client credentials with JWT ECDSA validation")
    @Tag("jwt")
    @Tag("ecdsa")
    void clientCredentialsWithJwtECDSAValidation() throws Exception {
        final String kafkaBootstrap = "localhost:9092";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "demo-ec";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        // For metrics
        final String jwksPath = "/realms/" + realm + "/protocol/openid-connect/certs";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtECDSAValidationTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_count for jwks > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for jwks > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWT", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 validations
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWT", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for jwks > 0.0");
    }

    @Test
    @DisplayName("Client credentials with JWT RSA validation")
    @Tag("jwt")
    @Tag("rsa")
    void clientCredentialsWithJwtRSAValidation() throws Exception {
        final String kafkaBootstrap = "localhost:9096";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "kafka-authz";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        // For metrics
        String jwksPath = "/realms/" + realm + "/protocol/openid-connect/certs";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtRSAValidationTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAIN", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "mechanism", "OAUTHBEARER", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAIN", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "mechanism", "OAUTHBEARER", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwks > 0.0");
    }

    @Test
    @DisplayName("Access token with introspection")
    @Tag("introspection")
    void accessTokenWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9093";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        final String introspectPath = "/realms/" + realm + "/protocol/openid-connect/token/introspect";

        final String tokenEndpointUri = "http://" + authHostPort + path;
        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // First, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-accessTokenWithIntrospectionTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        // Inter-broker connection did some validation, producer and consumer did some
        Assertions.assertTrue(value != null && value.intValue() >= 5, "strimzi_oauth_http_requests_count for introspect >= 5");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for introspect > 0.0");
    }

    @Test
    @DisplayName("Refresh token with introspection")
    @Tag("introspection")
    void refreshTokenWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9093";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        final String introspectPath = "/realms/" + realm + "/protocol/openid-connect/token/introspect";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        final String clientId = "kafka-cli";
        final String username = "alice";
        final String password = "alice-password";

        // First, request access token using client id and secret
        String refreshToken = loginWithUsernameForRefreshToken(URI.create(tokenEndpointUri), username, password, clientId);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, clientId);
        oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, refreshToken);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");
        oauthConfig.put(ClientConfig.OAUTH_SCOPE, "profile");

        final String topic = "KeycloakAuthenticationTest-refreshTokenWithIntrospectionTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        // On top of the access token test, producer and consumer together did 4 requests
        Assertions.assertTrue(value != null && value.intValue() >= 9, "strimzi_oauth_http_requests_count for introspect >= 9");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for introspect > 0.0");
    }

    @Test
    @DisplayName("Password grant with JWT RSA validation")
    @Tag("jwt")
    @Tag("password-grant")
    void passwordGrantWithJwtRSAValidation() throws Exception {
        final String kafkaBootstrap = "localhost:9096";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "kafka-authz";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-cli");
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, "alice");
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, "alice-password");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-passwordGrantWithJwtRSAValidationTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }
    }

    @Test
    @DisplayName("Password grant with introspection")
    @Tag("introspection")
    @Tag("password-grant")
    void passwordGrantWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9093";
        final String authHostPort = environment.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        final String username = "alice";
        final String password = "alice-password";
        final String clientId = "kafka";

        // Request access token using username and password with public client
        String accessToken = loginWithUsernamePassword(URI.create(tokenEndpointUri), username, password, clientId);

        TokenInfo tokenInfo = introspectAccessToken(accessToken,
                new PrincipalExtractor("preferred_username"));

        Assertions.assertEquals(username, tokenInfo.principal(), "Token contains 'preferred_username' claim with value equal to username");


        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, clientId);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, username);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, password);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-passwordGrantWithIntrospectionTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }


        // Authenticate using the username and password and a confidential client - different clientId + secret for consumer

        String confidentialClientId = "kafka-producer-client";
        String confidentialClientSecret = "kafka-producer-client-secret";

        accessToken = loginWithUsernamePassword(URI.create(tokenEndpointUri), username, password, confidentialClientId, confidentialClientSecret);

        tokenInfo = introspectAccessToken(accessToken,
                new PrincipalExtractor("preferred_username"));

        Assertions.assertEquals(username, tokenInfo.principal(), "Token contains 'preferred_username' claim with value equal to username");

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, confidentialClientId);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, confidentialClientSecret);

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

            Assertions.assertEquals(1, records.count(), "Got message");
            Assertions.assertEquals("The Message", records.iterator().next().value(), "Is message text: 'The Message'");
        }
    }
}
