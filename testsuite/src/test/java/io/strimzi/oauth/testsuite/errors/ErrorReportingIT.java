/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.errors;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.KeycloakErrorsTestEnvironment;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
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
import org.testcontainers.containers.GenericContainer;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import io.strimzi.oauth.testsuite.utils.KafkaClientConfig;

import static io.strimzi.oauth.testsuite.common.TestUtil.assertTrueExtra;
import static io.strimzi.oauth.testsuite.common.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.common.TestUtil.getRootCause;

/**
 * Tests for error reporting during OAuth authentication
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Error Reporting Tests")
public class ErrorReportingIT {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportingIT.class);

    private GenericContainer<?> kafkaContainer;
    private KeycloakErrorsTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new KeycloakErrorsTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    String getKafkaBootstrap(int port) {
        return "localhost:" + port;
    }

    void commonChecks(Throwable cause) {
        Assertions.assertEquals(SaslAuthenticationException.class, cause.getClass(), "Expected SaslAuthenticationException");
    }

    void checkErrId(String message) {
        Assertions.assertTrue(message.substring(message.length() - 16).startsWith("ErrId:"), "Error message is sanitised");
    }

    @Test
    @Tag(TestTags.JWT)
    @DisplayName("Unparseable JWT token")
    void testUnparseableJwtToken() throws Exception {
        String token = "unparseable";

        final String kafkaBootstrap = getKafkaBootstrap(9203);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-unparseableJwtTokenTest";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkUnparseableJwtTokenErrorMessage(cause.toString());
        }
    }

    void checkUnparseableJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Failed to parse JWT"), message);
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @DisplayName("Corrupt token with introspection")
    void testCorruptTokenIntrospect() throws Exception {
        String token = "corrupt";

        final String kafkaBootstrap = getKafkaBootstrap(9202);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-corruptTokenIntrospect";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCorruptTokenIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkCorruptTokenIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token not active"), message);
    }

    @Test
    @Tag(TestTags.JWT)
    @DisplayName("Invalid JWT token kid")
    void testInvalidJwtTokenKid() throws Exception {
        // We authenticate against 'demo' realm, but use it with listener configured with 'kafka-authz' realm
        final String kafkaBootstrap = getKafkaBootstrap(9203);
        final String hostPort = environment.getKeycloakHostPort();

        final String tokenEndpointUri = "http://" + hostPort + "/realms/demo/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-invalidJwtTokenKidTest";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkInvalidJwtTokenKidErrorMessage(cause.getMessage());
        }
    }

    void checkInvalidJwtTokenKidErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Unknown signing key (kid:"), message);
    }

    @Test
    @Tag(TestTags.JWT)
    @DisplayName("Forged JWT signature")
    void testForgedJwtSig() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9201);
        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "demo-ec";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-forgedJwtSig";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkForgedJwtSigErrorMessage(cause.getMessage());
        }
    }

    void checkForgedJwtSigErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Invalid token signature"), message);
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @DisplayName("Forged JWT signature with introspection")
    void testForgedJwtSigIntrospect() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9202);
        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "demo";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-forgedJwtSigIntrospect";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkForgedJwtSigIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkForgedJwtSigIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token not active"), message);
    }

    @Test
    @Tag(TestTags.JWT)
    @DisplayName("Expired JWT token")
    void testExpiredJwtToken() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9205);
        final String hostPort = environment.getKeycloakHostPort();
        final String realm = "expiretest";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");

        // sleep for 6s for token to expire
        Thread.sleep(6000);

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-expiredJwtTokenTest";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkExpiredJwtTokenErrorMessage(cause.getMessage());
        }
    }

    void checkExpiredJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token expired at: "), message);
    }

    @Test
    @Tag(TestTags.PLAIN)
    @DisplayName("Bad client ID with OAuth over PLAIN")
    void testBadClientIdOAuthOverPlain() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9204);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-inexistent");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = KafkaClientConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "ErrorReportingTests-badClientIdOAuthOverPlainTest";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkBadClientIdOAuthOverPlainErrorMessage(cause.getMessage());
        }
    }

    void checkBadClientIdOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    @Test
    @Tag(TestTags.PLAIN)
    @DisplayName("Bad secret with OAuth over PLAIN")
    void testBadSecretOAuthOverPlain() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9204);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-bad-secret");

        Properties producerProps = KafkaClientConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "ErrorReportingTests-badSecretOAuthOverPlainTest";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkBadCSecretOAuthOverPlainErrorMessage(cause.getMessage());
        }
    }

    void checkBadCSecretOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    @Test
    @Tag(TestTags.PLAIN)
    @DisplayName("Can't connect PLAIN with client credentials")
    void testCantConnectPlainWithClientCredentials() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9206);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = KafkaClientConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "ErrorReportingTests-cantConnectPlainWithClientCredentials";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCantConnectPlainWithClientCredentialsErrorMessage(cause.getMessage());
        }
    }

    void checkCantConnectPlainWithClientCredentialsErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @DisplayName("Can't connect to introspection endpoint")
    void testCantConnectIntrospect() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9207);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-cantConnectIntrospect";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCantConnectIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkCantConnectIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Runtime failure during token validation"), message);
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.TIMEOUT)
    @DisplayName("Can't connect to introspection endpoint with timeout")
    void testCantConnectIntrospectWithTimeout() throws Exception {
        final String kafkaBootstrap = getKafkaBootstrap(9208);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "ErrorReportingTests-cantConnectIntrospectWithTimeout";

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assertions.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCantConnectIntrospectErrorMessage(cause.getMessage());
            // get kafka log, parse it, find the errId, find 'connect timed out' string.
            checkKafkaLogConnectTimedOut(cause.getMessage());
        }
    }

    private void checkKafkaLogConnectTimedOut(String message) {
        String errId = message.substring(message.length() - 16, message.length() - 1);
        List<String> log = getContainerLogsForString(kafkaContainer, errId);
        // For JDK 17 the ConnectionTimeoutException error message was fixed to start with upper case
        long matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("onnect timed out")).count();
        if (matchedCount == 0) {
            matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("Connection refused")).count();
            Assertions.assertTrue(matchedCount > 0, "Found 'connect timed out' or 'Connection refused' cause of the error? (" + errId + ") " + log);
            ErrorReportingIT.log.warn("Found 'Connection refused' rather than 'Connect timed out'");
        }
    }

    @Test
    @Tag(TestTags.TIMEOUT)
    @DisplayName("Can't connect to Keycloak with timeout")
    void testCantConnectKeycloakWithTimeout() {
        final String kafkaBootstrap = getKafkaBootstrap(9208);
        final String hostPort = "172.0.0.221:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        int timeout = 5;
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");
        oauthConfig.put(ClientConfig.OAUTH_CONNECT_TIMEOUT_SECONDS, String.valueOf(timeout));

        Properties producerProps = KafkaClientConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        long start = System.currentTimeMillis();
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            Assertions.fail("Should fail with KafkaException");
        } catch (Exception e) {
            long diff = System.currentTimeMillis() - start;

            assertTrueExtra("is instanceof KafkaException", e instanceof KafkaException, e);
            assertTrueExtra("Failed due to LoginException", getRootCause(e).toString().contains("LoginException"), e);
            assertTrueExtra("Unexpected diff: " + diff, diff > timeout * 1000 && diff < timeout * 1000 + 1000, e);
        }
    }

}
