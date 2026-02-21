/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;

/**
 * Tests for HTTP connection timeout handling.
 * Validates that connection failures and timeouts are properly handled and reported.
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
public class ConnectTimeoutIT {

    private static final Logger log = LoggerFactory.getLogger(ConnectTimeoutIT.class);

    OAuthEnvironmentExtension env;
    private GenericContainer<?> kafkaContainer;

    @BeforeAll
    void setUp() {
        kafkaContainer = env.getKafka();
    }

    @Test
    @DisplayName("Connection failure to introspection endpoint should be properly reported")
    @Tag(TestTags.TIMEOUT)
    @Tag(TestTags.INTROSPECTION)
    void testCantConnectIntrospect() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        // Turn off the mockoauth server
        changeAuthServerMode("server", "mode_off");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, getInvalidMockAccessToken());

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {

            final String topic = "ConnectTimeoutTests-cantConnectIntrospect";
            try {
                producer.send(new ProducerRecord<>(topic, "The Message"))
                    .get();
                Assertions.fail("Should fail with ExecutionException");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                commonChecks(cause);
                checkCantConnectIntrospectErrorMessage(cause.getMessage());
                checkKafkaLogConnectionRefused(cause.getMessage());
            } finally {
                // Turn the mockoauth server back on
                changeAuthServerMode("server", "mode_cert_one_on");
            }
        }
    }

    @Test
    @DisplayName("Request timeout to token endpoint should be properly handled")
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
        try (KafkaProducer<String, String> ignored = new KafkaProducer<>(producerProps)) {

            Assertions.fail("Should fail with KafkaException");
        } catch (Exception e) {
            long diff = System.currentTimeMillis() - start;
            Assertions.assertTrue(e instanceof KafkaException, "is instanceof KafkaException");
            Assertions.assertTrue(getRootCause(e).toString()
                .contains("LoginException"), "Failed due to LoginException");
            Assertions.assertTrue(diff > timeoutOverride * 1000 && diff < timeoutOverride * 1000 + 1000, "Unexpected diff: " + diff);
        } finally {
            changeAuthServerMode("token", "mode_200");
            System.clearProperty("oauth.read.timeout.seconds");
        }
    }

    private String getInvalidMockAccessToken() {
        // If token contains '=' it is rejected by the server before it even reaches the ValidatorCallbackHandler
        // Some validation of allowed characters is clearly performed in order to short-circuit the validation
        return "mock.access.token";
    }

    void commonChecks(Throwable cause) {
        Assertions.assertEquals(SaslAuthenticationException.class, cause.getClass(), "Expected SaslAuthenticationException");
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
