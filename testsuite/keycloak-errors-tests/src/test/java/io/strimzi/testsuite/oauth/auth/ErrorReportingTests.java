/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.Assert;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static io.strimzi.testsuite.oauth.common.TestUtil.getRootCause;

public class ErrorReportingTests {

    private final String kafkaContainer;

    ErrorReportingTests(String kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    void doTests() throws Exception {
        unparseableJwtToken();
        corruptTokenIntrospect();
        invalidJwtTokenKid();
        forgedJwtSig();
        forgedJwtSigIntrospect();
        expiredJwtToken();
        badClientIdOAuthOverPlain();
        badSecretOAuthOverPlain();
        cantConnectPlainWithClientCredentials();
        cantConnectIntrospect();
        cantConnectIntrospectWithTimeout();
        cantConnectKeycloakWithTimeout();
    }

    String getKafkaBootstrap(int port) {
        return "kafka:" + port;
    }

    void commonChecks(Throwable cause) {
        Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
    }

    void checkErrId(String message) {
        Assert.assertTrue("Error message is sanitised", message.substring(message.length() - 16).startsWith("ErrId:"));
    }

    private void unparseableJwtToken() throws Exception {
        String token = "unparseable";

        System.out.println("    ====    KeycloakErrorsTest :: unparseableJwtToken");

        final String kafkaBootstrap = getKafkaBootstrap(9203);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-unparseableJwtTokenTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkUnparseableJwtTokenErrorMessage(cause.toString());
        }
    }

    void checkUnparseableJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Failed to parse JWT"));
    }

    private void corruptTokenIntrospect() throws Exception {
        String token = "corrupt";

        System.out.println("    ====    KeycloakErrorsTest :: corruptTokenIntrospect");

        final String kafkaBootstrap = getKafkaBootstrap(9202);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-corruptTokenIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCorruptTokenIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkCorruptTokenIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Token not active"));
    }

    private void invalidJwtTokenKid() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: invalidJwtTokenKid");

        // We authenticate against 'demo' realm, but use it with listener configured with 'kafka-authz' realm
        final String kafkaBootstrap = getKafkaBootstrap(9203);
        final String hostPort = "keycloak:8080";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/demo/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-invalidJwtTokenKidTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkInvalidJwtTokenKidErrorMessage(cause.getMessage());
        }
    }

    void checkInvalidJwtTokenKidErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Unknown signing key (kid:"));
    }

    private void forgedJwtSig() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: forgedJwtSig");

        final String kafkaBootstrap = getKafkaBootstrap(9201);
        final String hostPort = "keycloak:8080";
        final String realm = "demo-ec";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-forgedJwtSig";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkForgedJwtSigErrorMessage(cause.getMessage());
        }
    }

    void checkForgedJwtSigErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Invalid token signature"));
    }

    private void forgedJwtSigIntrospect() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: forgedJwtSigIntrospect");

        final String kafkaBootstrap = getKafkaBootstrap(9202);
        final String hostPort = "keycloak:8080";
        final String realm = "demo";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-forgedJwtSigIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkForgedJwtSigIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkForgedJwtSigIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Token not active"));
    }

    private void expiredJwtToken() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: expiredJwtToken");

        final String kafkaBootstrap = getKafkaBootstrap(9205);
        final String hostPort = "keycloak:8080";
        final String realm = "expiretest";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        // sleep for 6s for token to expire
        Thread.sleep(6000);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-expiredJwtTokenTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkExpiredJwtTokenErrorMessage(cause.getMessage());
        }
    }

    void checkExpiredJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Token expired at: "));
    }

    private void badClientIdOAuthOverPlain() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: badClientIdOAuthOverPlain");

        final String kafkaBootstrap = getKafkaBootstrap(9204);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-inexistent");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-badClientIdOAuthOverPlainTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkBadClientIdOAuthOverPlainErrorMessage(cause.getMessage());
        }
    }

    void checkBadClientIdOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    private void badSecretOAuthOverPlain() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: badSecretOAuthOverPlain");

        final String kafkaBootstrap = getKafkaBootstrap(9204);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-bad-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-badSecretOAuthOverPlainTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkBadCSecretOAuthOverPlainErrorMessage(cause.getMessage());
        }
    }

    void checkBadCSecretOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    private void cantConnectPlainWithClientCredentials() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: cantConnectPlainWithClientCredentials");

        final String kafkaBootstrap = getKafkaBootstrap(9206);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-cantConnectPlainWithClientCredentials";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCantConnectPlainWithClientCredentialsErrorMessage(cause.getMessage());
        }
    }

    void checkCantConnectPlainWithClientCredentialsErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    private void cantConnectIntrospect() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: cantConnectIntrospect");

        final String kafkaBootstrap = getKafkaBootstrap(9207);
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-cantConnectIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            commonChecks(cause);
            checkCantConnectIntrospectErrorMessage(cause.getMessage());
        }
    }

    void checkCantConnectIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assert.assertTrue(message.contains("Runtime failure during token validation"));
    }

    private void cantConnectIntrospectWithTimeout() throws Exception {
        System.out.println("    ====    KeycloakErrorsTest :: cantConnectIntrospectWithTimeout");

        final String kafkaBootstrap = getKafkaBootstrap(9208);
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakErrorsTest-cantConnectIntrospectWithTimeout";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
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
            System.out.println("");
            matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("Connection refused")).count();
            Assert.assertTrue("Found 'connect timed out' or 'Connection refused' cause of the error? (" + errId + ") " + log, matchedCount > 0);
            System.out.println("WARN: Found 'Connection refused' rather than 'Connect timed out'");
        }
    }

    private void cantConnectKeycloakWithTimeout() {
        System.out.println("    ====    KeycloakErrorsTest :: cantConnectKeycloakWithTimeout");

        final String kafkaBootstrap = getKafkaBootstrap(9208);
        final String hostPort = "172.0.0.221:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        int timeout = 5;
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");
        oauthConfig.put(ClientConfig.OAUTH_CONNECT_TIMEOUT_SECONDS, String.valueOf(timeout));

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        long start = System.currentTimeMillis();
        try {
            Producer<String, String> producer = new KafkaProducer<>(producerProps);
            Assert.fail("Should fail with KafkaException");
        } catch (Exception e) {
            long diff = System.currentTimeMillis() - start;
            Assert.assertTrue("is instanceof KafkaException", e instanceof KafkaException);
            Assert.assertTrue("Failed due to LoginException", getRootCause(e).toString().contains("LoginException"));
            Assert.assertTrue("Unexpected diff: " + diff, diff > timeout * 1000 && diff < timeout * 1000 + 1000);
        }
    }

}
