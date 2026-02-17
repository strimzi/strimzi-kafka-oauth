/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
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
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static io.strimzi.testsuite.oauth.mockoauth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.mockoauth.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.common.TestUtil.getRootCause;
import static io.strimzi.testsuite.oauth.mockoauth.Common.loginWithClientSecret;

/**
 * Tests for HTTP retry handling.
 * Validates that HTTP retries are properly configured and work for various endpoints.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RetriesIT {
    // FAILINGINTROSPECT and FAILINGJWT listeners are configured with 'oauth.http.retry.pause.millis' of 3000
    static final int PAUSE_MILLIS = 3000;

    private MockOAuthTestEnvironment environment;
    private GenericContainer<?> kafkaContainer;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
        environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() throws IOException {
        environment = new MockOAuthTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Broker should retry token, introspection, and userinfo endpoints on PLAIN")
    @Tag("retry")
    @Tag("plain")
    @Tag("introspection")
    void testPlainIntrospectAndUserinfoEndpointsRetries() throws Exception {
        // Use FAILINGINTROSPECT kafka listener that uses /failing_introspect, /failing_userinfo,
        // and /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = "localhost:9097";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);
        createOAuthClient("kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            // configure the endpoints so that they are in failing mode
            changeAuthServerMode("failing_token", "mode_400");
            changeAuthServerMode("failing_introspect", "mode_500");
            changeAuthServerMode("failing_userinfo", "mode_503");

            String topic = "RetriesTests-plainIntrospectAndUserinfoEndpointsRetriesTest";

            // try to produce
            long start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message"))
                .get();

            // check that at least 9 seconds have passed - 3 for token retry, 3 for introspect retry, and 3 for userinfo retry
            // due to retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assertions.assertTrue(System.currentTimeMillis() - start > 3 * PAUSE_MILLIS, "It should take at least 9 seconds to complete");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Client should retry token endpoint requests according to http.retries config")
    @Tag("retry")
    @Tag("client")
    void testClientRetries() throws Exception {
        final String kafkaBootstrap = "localhost:9096";
        final String hostPort = Common.getMockOAuthAuthHostPort();

        final String tokenEndpointUri = "https://" + hostPort + "/failing_token";

        // token endpoint set to failing
        changeAuthServerMode("failing_token", "MODE_400");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        // configure producer with no http.retries
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, testClient);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, testSecret);
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        try {
            new KafkaProducer<>(producerProps);

            Assertions.fail("Should fail with KafkaException");
        } catch (Exception e) {
            // token endpoint is tried and fails
            // get the exception

            Assertions.assertTrue(e instanceof KafkaException, "is instanceof KafkaException");
            Assertions.assertTrue(getRootCause(e).toString()
                .contains("LoginException"), "Failed due to LoginException");
        }

        // repeat, it should succeed
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            // producer created successfully
        }

        // now create a new producer with http.retries = 1 and some pause
        oauthConfig.put(Config.OAUTH_HTTP_RETRIES, "1");
        oauthConfig.put(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, String.valueOf(PAUSE_MILLIS));

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        long start = System.currentTimeMillis();
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            // token endpoint is tried and fails
            // should automatically retry and succeed
            Assertions.assertTrue(System.currentTimeMillis() - start > PAUSE_MILLIS, "It should take at least 3 seconds to get a token");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Broker should retry token endpoint on PLAIN with JWKS endpoint configured")
    @Tag("retry")
    @Tag("plain")
    @Tag("jwks")
    void testPlainRetriesWithJWKS() throws Exception {
        // Use FAILINGJWT kafka listener that uses /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = "localhost:9098";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);
        createOAuthClient("kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);

        // JWKS endpoint is already set to MODE_200 by MockOAuthTestEnvironment.start()
        // and keys are loaded during Kafka startup. Verify keys were loaded.
        List<String> jwksLog = getContainerLogsForString(kafkaContainer, "JWKS keys change detected");
        Assertions.assertTrue(jwksLog.size() > 0, "JWKS keys should have been loaded during Kafka startup");

        // set failing_token endpoint to always return 500, so that the retry will fail
        changeAuthServerMode("failing_token", "mode_failing_500");


        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            String topic = "RetriesTests-plainRetriesWithJWKSTest";

            // try to produce
            long start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message"))
                    .get();
                Assertions.fail("Should have failed due to failing_token set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            long diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assertions.assertTrue(diff > PAUSE_MILLIS, "It should take at least 3 seconds to fail (" + diff + ")");


            // configure failing_token endpoint so that they only fail every other time
            changeAuthServerMode("failing_token", "mode_400");

            start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message"))
                .get();

            diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assertions.assertTrue(diff > PAUSE_MILLIS, "It should take at least 3 seconds due to token retry (" + diff + ")");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Broker should retry introspection and userinfo endpoint requests on OAUTHBEARER")
    @Tag("retry")
    @Tag("introspection")
    void testIntrospectAndUserinfoEndpointsRetries() throws Exception {
        // use kafka listener that uses /failing_introspect and failing_userinfo
        final String kafkaBootstrap = "localhost:9097";
        final String hostPort = Common.getMockOAuthAuthHostPort();
        final String tokenEndpointUri = "https://" + hostPort + "/token";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        createOAuthClient("kafka", "kafka-secret");

        // authenticate oauth with accesstoken
        changeAuthServerMode("token", "mode_200");
        String accessToken = loginWithClientSecret(tokenEndpointUri, testClient, testSecret, "../docker/target/kafka/certs/ca-truststore.p12", "changeit");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, accessToken);
        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // set failing_introspect endpoint to always return 500, so that the retry will fail
        changeAuthServerMode("failing_introspect", "mode_failing_500");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            String topic = "RetriesTests-introspectAndUserinfoEndpointsRetriesTest";

            // try to produce
            long start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message"))
                    .get();
                Assertions.fail("Should have failed due to failing_introspect set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            // check that at least 3 seconds have passed - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assertions.assertTrue(System.currentTimeMillis() - start > PAUSE_MILLIS, "It should take at least 3 seconds to fail");


            // set failing_introspect to mode_400, so it will fail but will be immediately retried and will succeed
            changeAuthServerMode("failing_introspect", "mode_400");

            // set failing_userinfo to mode_failing_500 so that a retry will fail
            changeAuthServerMode("failing_userinfo", "mode_failing_500");

            // authenticate oauth
            start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message"))
                    .get();
                Assertions.fail("Should have failed due to failing_introspect set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assertions.assertTrue(System.currentTimeMillis() - start > 2 * PAUSE_MILLIS, "It should take at least 6 seconds to fail");


            // set failing_userinfo to mode_500 so that both failing_introspect and failing_userinfo can recover
            changeAuthServerMode("failing_userinfo", "mode_500");

            // authenticate oauth
            start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message"))
                .get();

            // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assertions.assertTrue(System.currentTimeMillis() - start > 2 * PAUSE_MILLIS, "It should take at least 6 seconds for double recovery");
        }
    }
}
