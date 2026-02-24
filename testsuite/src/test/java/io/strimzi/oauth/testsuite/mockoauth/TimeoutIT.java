/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.expectSaslAuthFailure;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;

/**
 * Tests for HTTP connection timeout and client-side retry handling.
 * Validates that connection failures, timeouts, and client retries are properly handled and reported.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        oauthProperties = {
            "oauth.config.id=INTROSPECTTIMEOUT",
            "oauth.connect.timeout.seconds=5",
            "oauth.introspection.endpoint.uri=https://mockoauth:8090/introspect",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class TimeoutIT {

    private static final Logger log = LoggerFactory.getLogger(TimeoutIT.class);

    // Retry pause used in client retries test
    static final int PAUSE_MILLIS = 3000;

    OAuthEnvironmentExtension env;
    private GenericContainer<?> kafkaContainer;

    @BeforeAll
    void setUp() {
        kafkaContainer = env.getKafka();
    }

    @Test
    @Tag(TestTags.TIMEOUT)
    @Tag(TestTags.INTROSPECTION)
    void testCantConnectIntrospect() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        // Turn off the mockoauth server
        changeAuthServerMode("server", "mode_off");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, getInvalidMockAccessToken());

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        final String topic = "ConnectTimeoutTests-cantConnectIntrospect";
        try {
            expectSaslAuthFailure(producerProps, topic, msg -> {
                checkCantConnectIntrospectErrorMessage(msg);
                checkKafkaLogConnectionRefused(msg);
            });
        } finally {
            // Turn the mockoauth server back on
            changeAuthServerMode("server", "mode_cert_one_on");
        }
    }

    @Test
    @Tag(TestTags.TIMEOUT)
    void testConnectAuthServerWithTimeout() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = MockOAuthAdmin.getMockOAuthAuthHostPort();

        final String tokenEndpointUri = "https://" + hostPort + "/token";

        // Make the token endpoint accept a connection but not return anything
        changeAuthServerMode("token", "mode_stall");

        // Let's also test system property override
        int timeoutOverride = 10;
        System.setProperty("oauth.read.timeout.seconds", String.valueOf(timeoutOverride));

        int ignoredTimeout = 5;
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "ignored.id");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "ignored.secret");
        oauthConfig.put(ClientConfig.OAUTH_READ_TIMEOUT_SECONDS, String.valueOf(ignoredTimeout));
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        long start = System.currentTimeMillis();
        try {
            produceMessage(producerProps, "ConnectTimeoutTests-connectAuthServerWithTimeout", "The Message");
            Assertions.fail("Should fail with KafkaException");
        } catch (KafkaException e) {
            long diff = System.currentTimeMillis() - start;
            Assertions.assertTrue(getRootCause(e).toString()
                .contains("LoginException"), "Failed due to LoginException");
            Assertions.assertTrue(diff > timeoutOverride * 1000 && diff < timeoutOverride * 1000 + 1000, "Unexpected diff: " + diff);
        } finally {
            changeAuthServerMode("token", "mode_200");
            System.clearProperty("oauth.read.timeout.seconds");
        }
    }

    @Test
    @Tag(TestTags.RETRY)
    @Tag(TestTags.CLIENT)
    void testClientRetries() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = MockOAuthAdmin.getMockOAuthAuthHostPort();

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
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // Create producer - should fail because token endpoint returns 400 on first request.
        // We only test constructor (SASL login) here, not message sending, because the mock
        // /failing_token endpoint produces tokens without a 'sub' claim which would fail
        // broker-side introspection validation.
        try {
            new KafkaProducer<>(producerProps);
            Assertions.fail("Should fail with KafkaException");
        } catch (KafkaException e) {
            // token endpoint is tried and fails
            Assertions.assertTrue(getRootCause(e).toString()
                .contains("LoginException"), "Failed due to LoginException");
        }

        // repeat, it should succeed (alternating mode returns 200 on second request)
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            // producer created successfully
        }

        // now create a new producer with http.retries = 1 and some pause
        oauthConfig.put(Config.OAUTH_HTTP_RETRIES, "1");
        oauthConfig.put(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, String.valueOf(PAUSE_MILLIS));

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer - token endpoint is tried and fails, should automatically retry and succeed
        long start = System.currentTimeMillis();
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            Assertions.assertTrue(System.currentTimeMillis() - start > PAUSE_MILLIS, "It should take at least 3 seconds to get a token");
        }
    }

    private String getInvalidMockAccessToken() {
        // If token contains '=' it is rejected by the server before it even reaches the ValidatorCallbackHandler
        // Some validation of allowed characters is clearly performed in order to short-circuit the validation
        return "mock.access.token";
    }

    void checkCantConnectIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Runtime failure during token validation"));
    }

    void checkErrId(String message) {
        Assertions.assertTrue(message.substring(message.length() - 16)
            .startsWith("ErrId:"), "Error message is sanitised");
    }

    private void checkKafkaLogConnectionRefused(String message) {
        String errId = message.substring(message.length() - 16, message.length() - 1);
        // Verify the authentication failure with this errId was logged
        List<String> errIdLog = getContainerLogsForString(kafkaContainer, errId);
        Assertions.assertFalse(errIdLog.isEmpty(), "Error with " + errId + " should appear in container logs");
        // The full stack trace with "Connection refused" is in the "Runtime failure" log entry,
        // not in the "Action failed" entries which are single-line
        List<String> log = getContainerLogsForString(kafkaContainer, "Runtime failure during token validation");
        long matchedCount = log.stream()
            .filter(s -> s.startsWith("Caused by:") && s.contains("Connection refused"))
            .count();
        Assertions.assertTrue(matchedCount > 0, "Found 'connection refused' cause of the error? (" + errId + ")");
    }
}
