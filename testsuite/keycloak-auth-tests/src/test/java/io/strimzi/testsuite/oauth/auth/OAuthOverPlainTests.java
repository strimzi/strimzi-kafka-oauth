/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.testsuite.oauth.auth.Common.buildConsumerConfigPlain;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.auth.Common.poll;
import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;
import static java.util.Collections.singletonList;

public class OAuthOverPlainTests {

    private static final Logger log = LoggerFactory.getLogger(OAuthOverPlainTests.class);

    static void doTests() throws Exception {
        clientCredentialsOverPlainWithJwt();

        clientCredentialsOverPlainWithIntrospection();
        // the next test depends on the previous test so it has to come after
        accessTokenOverPlainWithIntrospection();

        clientCredentialsOverPlainWithFloodTest();
        accessTokenOverPlainWithClientCredentialsDisabled();
        clientCredentialsOverPlainWithClientCredentialsDisabled();
    }

    static void accessTokenOverPlainWithClientCredentialsDisabled() throws Exception {

        System.out.println("    ====    KeycloakAuthenticationTest :: accessTokenOverPlainWithClientCredentialsDisabled");

        final String kafkaBootstrap = "kafka:9103";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/auth/realms/" + realm + "/protocol/openid-connect/token";

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

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_validation_requests_count for jwks >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener
        // Validation did not require the broker authenticating in client's name, because the token was passed
        Assert.assertEquals("strimzi_oauth_http_requests_count for plain == 0", 0, value.intValue());

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");
        Assert.assertEquals("strimzi_oauth_http_requests_totaltimems for plain == 0.0", 0.0, value.doubleValue(), 0.0);
    }

    static void clientCredentialsOverPlainWithClientCredentialsDisabled() throws Exception {

        System.out.println("    ====    KeycloakAuthenticationTest :: clientCredentialsOverPlainWithClientCredentialsDisabled");

        final String kafkaBootstrap = "kafka:9103";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithClientCredentialsDisabled";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should have failed due to client credentials being disabled on the server");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof SaslAuthenticationException)) {
                Assert.fail("Should have failed with AuthenticationException but was " + e.getCause());
            }
        }
    }

    static void clientCredentialsOverPlainWithIntrospection() throws Exception {

        System.out.println("    ====    KeycloakAuthenticationTest :: clientCredentialsOverPlainWithIntrospection");

        final String kafkaBootstrap = "kafka:9097";

        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        // For metrics
        String tokenEndpointPath = "/auth/realms/" + realm + "/protocol/openid-connect/token";

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

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_validation_requests_count for introspect >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_totaltimems for introspect > 0.0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_http_requests_count for plain >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for plain > 0.0", value.doubleValue() > 0.0);
    }

    static void accessTokenOverPlainWithIntrospection() throws Exception {

        System.out.println("    ====    KeycloakAuthenticationTest :: accessTokenOverPlainWithIntrospection");

        final String kafkaBootstrap = "kafka:9097";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/auth/realms/" + realm + "/protocol/openid-connect/token";

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

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 (on top of the previous test)
        Assert.assertTrue("strimzi_oauth_validation_requests_count for introspect >= 8", value != null && value.intValue() >= 8);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_http_requests_count for plain >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for plain > 0.0", value.doubleValue() > 0.0);
    }

    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     *
     * It connects to the Kafka using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     *
     * @throws Exception Any uncaught exception
     */
    static void clientCredentialsOverPlainWithJwt() throws Exception {

        System.out.println("    ====    KeycloakAuthenticationTest :: clientCredentialsOverPlainWithJwt");

        final String kafkaBootstrap = "kafka:9096";
        final String hostPort = "keycloak:8080";

        // For metrics
        final String realm = "kafka-authz";
        String tokenEndpointPath = "/auth/realms/" + realm + "/protocol/openid-connect/token";

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

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_validation_requests_count for jwt >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_totaltimems for jwt > 0.0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assert.assertTrue("strimzi_oauth_http_requests_count for plain >= 4", value != null && value.intValue() >= 4);

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAIN", "kind", "plain", "host", hostPort, "path", tokenEndpointPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for plain > 0.0", value.doubleValue() > 0.0);
    }

    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     *
     * It connects concurrently with multiple producers with different client IDs using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     */
    static void clientCredentialsOverPlainWithFloodTest() {

        System.out.println("    ====    KeycloakAuthenticationTest :: clientCredentialsOverPlainWithFloodTest");

        final String kafkaBootstrap = "kafka:9102";

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
}
