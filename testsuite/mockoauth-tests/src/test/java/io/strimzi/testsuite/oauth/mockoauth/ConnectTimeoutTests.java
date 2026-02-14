/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static io.strimzi.testsuite.oauth.mockoauth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.common.TestUtil.getRootCause;

public class ConnectTimeoutTests {

    private final GenericContainer<?> kafkaContainer;

    public ConnectTimeoutTests(GenericContainer<?> kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    public void doTest() throws Exception {
        cantConnectIntrospect();
        connectAuthServerWithTimeout();
    }

    private void cantConnectIntrospect() throws Exception {
        System.out.println("    ====    Check the error returned if connection to the introspection endpoint fails");

        final String kafkaBootstrap = "localhost:9096";

        // Turn off the mockoauth server
        changeAuthServerMode("server", "mode_off");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, getInvalidMockAccessToken());

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {

            final String topic = "ConnectTimeoutTests-cantConnectIntrospect";
            try {
                producer.send(new ProducerRecord<>(topic, "The Message")).get();
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

    private void connectAuthServerWithTimeout() throws Exception {
        System.out.println("    ====    Check the error returned if request to the introspection endpoint times out");

        final String kafkaBootstrap = "localhost:9096";
        final String hostPort = Common.getMockOAuthAuthHostPort();

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
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        long start = System.currentTimeMillis();
        try (KafkaProducer<String, String> ignored = new KafkaProducer<>(producerProps)) {

            Assertions.fail("Should fail with KafkaException");
        } catch (Exception e) {
            long diff = System.currentTimeMillis() - start;
            Assertions.assertTrue(e instanceof KafkaException, "is instanceof KafkaException");
            Assertions.assertTrue(getRootCause(e).toString().contains("LoginException"), "Failed due to LoginException");
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
        Assertions.assertTrue(message.substring(message.length() - 16).startsWith("ErrId:"), "Error message is sanitised");
    }

    private void checkKafkaLogConnectionRefused(String message) {
        String errId = message.substring(message.length() - 16, message.length() - 1);
        // Verify the authentication failure with this errId was logged
        List<String> errIdLog = getContainerLogsForString(kafkaContainer, errId);
        Assertions.assertFalse(errIdLog.isEmpty(), "Error with " + errId + " should appear in container logs");
        // The detailed cause is in a separate log entry in newer Kafka versions
        List<String> log = getContainerLogsForString(kafkaContainer, "Action failed");
        long matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("Connection refused")).count();
        Assertions.assertTrue(matchedCount > 0, "Found 'connection refused' cause of the error? (" + errId + ")");
    }
}
