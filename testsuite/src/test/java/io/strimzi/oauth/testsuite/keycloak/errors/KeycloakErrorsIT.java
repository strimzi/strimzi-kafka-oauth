/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.errors;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;

import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.expectSaslAuthFailure;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.assertTrueExtra;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;

/**
 * Tests for errors during OAuth authentication using Keycloak.
 *
 * Uses per-method {@code @KafkaConfig} annotations to restart Kafka with the required
 * listener configuration for each error scenario. Tests that work with the class-level
 * config (realm=demo-ec, JWT) don't need a method-level annotation.
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "demo-ec",
        oauthProperties = {
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class KeycloakErrorsIT {

    static final Logger log = LoggerFactory.getLogger(KeycloakErrorsIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.JWT)
    public void unparseableJwtToken() throws Exception {
        String token = "unparseable";

        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-unparseableJwtTokenTest";

        expectSaslAuthFailure(producerProps, topic, this::checkUnparseableJwtTokenErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.INTROSPECTION)
    @KafkaConfig(
        realm = "demo",
        oauthProperties = {
            "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/demo/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void corruptTokenIntrospect() throws Exception {
        String token = "corrupt";

        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-corruptTokenIntrospect";

        expectSaslAuthFailure(producerProps, topic, this::checkCorruptTokenIntrospectErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.JWT)
    @KafkaConfig(
        realm = "kafka-authz",
        oauthProperties = {
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void invalidJwtTokenKid() throws Exception {
        // We authenticate against 'demo' realm, but use it with listener configured with 'kafka-authz' realm
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();

        final String tokenEndpointUri = "http://" + hostPort + "/realms/demo/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-invalidJwtTokenKidTest";

        expectSaslAuthFailure(producerProps, topic, this::checkInvalidJwtTokenKidErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.JWT)
    public void forgedJwtSig() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
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

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-forgedJwtSig";

        expectSaslAuthFailure(producerProps, topic, this::checkForgedJwtSigErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.INTROSPECTION)
    @KafkaConfig(
        realm = "demo",
        oauthProperties = {
            "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/demo/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void forgedJwtSigIntrospect() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
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

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-forgedJwtSigIntrospect";

        expectSaslAuthFailure(producerProps, topic, this::checkForgedJwtSigIntrospectErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.JWT)
    @KafkaConfig(
        realm = "expiretest",
        oauthProperties = {
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void expiredJwtToken() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
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

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-expiredJwtTokenTest";

        expectSaslAuthFailure(producerProps, topic, this::checkExpiredJwtTokenErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.PLAIN)
    @KafkaConfig(
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        realm = "kafka-authz",
        oauthProperties = {
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void badClientIdOAuthOverPlain() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-inexistent");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "KeycloakErrorsTest-badClientIdOAuthOverPlainTest";

        expectSaslAuthFailure(producerProps, topic, this::checkBadClientIdOAuthOverPlainErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.PLAIN)
    @KafkaConfig(
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        realm = "kafka-authz",
        oauthProperties = {
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void badSecretOAuthOverPlain() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-bad-secret");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "KeycloakErrorsTest-badSecretOAuthOverPlainTest";

        expectSaslAuthFailure(producerProps, topic, this::checkBadSecretOAuthOverPlainErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.PLAIN)
    @KafkaConfig(
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        realm = "kafka-authz",
        oauthProperties = {
            "oauth.token.endpoint.uri=http://keycloak:8099/realms/kafka-authz/protocol/openid-connect/token",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void cantConnectPlainWithClientCredentials() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigPlain(kafkaBootstrap, plainConfig);

        final String topic = "KeycloakErrorsTest-cantConnectPlainWithClientCredentials";

        expectSaslAuthFailure(producerProps, topic, this::checkCantConnectPlainWithClientCredentialsErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.INTROSPECTION)
    @KafkaConfig(
        realm = "demo",
        oauthProperties = {
            "oauth.introspection.endpoint.uri=http://keycloak:8099/realms/demo/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void cantConnectIntrospect() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-cantConnectIntrospect";

        expectSaslAuthFailure(producerProps, topic, this::checkCantConnectIntrospectErrorMessage);
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.TIMEOUT)
    @KafkaConfig(
        realm = "demo",
        oauthProperties = {
            "oauth.introspection.endpoint.uri=http://192.168.0.221:8080/realms/demo/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void cantConnectIntrospectWithTimeout() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        final String topic = "KeycloakErrorsTest-cantConnectIntrospectWithTimeout";

        expectSaslAuthFailure(producerProps, topic, message -> {
            checkCantConnectIntrospectErrorMessage(message);
            // get kafka log, parse it, find the errId, find 'connect timed out' string.
            checkKafkaLogConnectTimedOut(message);
        });
    }

    @Test
    @Tag(TestTags.ERROR_HANDLING)
    @Tag(TestTags.TIMEOUT)
    public void cantConnectKeycloakWithTimeout() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
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

        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        long start = System.currentTimeMillis();
        try {
            produceMessage(producerProps, "KeycloakErrorsTest-cantConnectKeycloakWithTimeout", "The Message");
            Assertions.fail("Should fail with KafkaException");
        } catch (KafkaException e) {
            long diff = System.currentTimeMillis() - start;

            assertTrueExtra("Failed due to LoginException", getRootCause(e).toString().contains("LoginException"), e);
            assertTrueExtra("Unexpected diff: " + diff, diff > timeout * 1000 && diff < timeout * 1000 + 1000, e);
        }
    }

    // Helper methods

    void checkErrId(String message) {
        Assertions.assertTrue(message.substring(message.length() - 16).startsWith("ErrId:"), "Error message is sanitised");
    }

    void checkUnparseableJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Failed to parse JWT"), message);
    }

    void checkCorruptTokenIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token not active"), message);
    }

    void checkInvalidJwtTokenKidErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Unknown signing key (kid:"), message);
    }

    void checkForgedJwtSigErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Invalid token signature"), message);
    }

    void checkForgedJwtSigIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token not active"), message);
    }

    void checkExpiredJwtTokenErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Token expired at: "), message);
    }

    void checkBadClientIdOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    void checkBadSecretOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    void checkCantConnectPlainWithClientCredentialsErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        Assertions.assertTrue(message.contains("credentials for user could not be verified"), message);
    }

    void checkCantConnectIntrospectErrorMessage(String message) {
        checkErrId(message);
        Assertions.assertTrue(message.contains("Runtime failure during token validation"), message);
    }

    private void checkKafkaLogConnectTimedOut(String message) {
        String errId = message.substring(message.length() - 16, message.length() - 1);
        List<String> log = getContainerLogsForString(env.getKafka(), errId);
        // For JDK 17 the ConnectionTimeoutException error message was fixed to start with upper case
        long matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("onnect timed out")).count();
        if (matchedCount == 0) {
            matchedCount = log.stream().filter(s -> s.startsWith("Caused by:") && s.contains("Connection refused")).count();
            Assertions.assertTrue(matchedCount > 0, "Found 'connect timed out' or 'Connection refused' cause of the error? (" + errId + ") " + log);
            KeycloakErrorsIT.log.warn("Found 'Connection refused' rather than 'Connect timed out'");
        }
    }
}
