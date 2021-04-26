/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.Assert;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigPlain;

public class ErrorReportingTests {

    static void doTests() throws Exception {
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
    }

    private static void unparseableJwtToken() throws Exception {
        String token = "unparseable";

        System.out.println("==== KeycloakAuthenticationTest :: unparseableJwtToken ====");

        final String kafkaBootstrap = "kafka:9096";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-unparseableJwtTokenTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Failed to parse JWT"));
        }
    }

    private static void corruptTokenIntrospect() throws Exception {
        String token = "corrupt";

        System.out.println("==== KeycloakAuthenticationTest :: corruptTokenIntrospect ====");

        final String kafkaBootstrap = "kafka:9093";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-corruptTokenIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Token has expired"));
        }
    }

    private static void invalidJwtTokenKid() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: invalidJwtTokenKid ====");

        // We authenticate against 'demo' realm, but use it with listener configured with 'kafka-authz' realm
        final String kafkaBootstrap = "kafka:9096";
        final String hostPort = "keycloak:8080";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/demo/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-invalidJwtTokenKidTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Unknown signing key (kid:"));
        }
    }

    private static void forgedJwtSig() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: forgedJwtSig ====");

        final String kafkaBootstrap = "kafka:9092";
        final String hostPort = "keycloak:8080";
        final String realm = "demo-ec";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-forgedJwtSig";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Invalid token signature"));
        }
    }

    private static void forgedJwtSigIntrospect() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: forgedJwtSigIntrospect ====");

        final String kafkaBootstrap = "kafka:9093";
        final String hostPort = "keycloak:8080";
        final String realm = "demo";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null);

        Map<String, String> oauthConfig = new HashMap<>();
        String tokenWithBrokenSig = info.token().substring(0, info.token().length() - 6) + "ffffff";

        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, tokenWithBrokenSig);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-forgedJwtSigIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Token has expired"));
        }

    }

    private static void expiredJwtToken() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: expiredJwtToken ====");

        final String kafkaBootstrap = "kafka:9104";
        final String hostPort = "keycloak:8080";
        final String realm = "expiretest";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        // sleep for 6s for token to expire
        Thread.sleep(6000);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-expiredJwtTokenTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Token expired at: "));
        }
    }

    private static void badClientIdOAuthOverPlain() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: badClientIdOAuthOverPlain ====");

        final String kafkaBootstrap = "kafka:9097";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-inexistent");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-badClientIdOAuthOverPlainTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("credentials for user could not be verified"));
        }
    }

    private static void badSecretOAuthOverPlain() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: badSecretOAuthOverPlain ====");

        final String kafkaBootstrap = "kafka:9097";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-bad-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-badSecretOAuthOverPlainTest";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("credentials for user could not be verified"));
        }
    }

    private static void cantConnectPlainWithClientCredentials() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: cantConnectPlainWithClientCredentials ====");

        final String kafkaBootstrap = "kafka:9105";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-cantConnectPlainWithClientCredentials";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("credentials for user could not be verified"));
        }
    }

    private static void cantConnectIntrospect() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: cantConnectIntrospect ====");

        final String kafkaBootstrap = "kafka:9106";
        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-cantConnectIntrospect";

        try {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            Assert.fail("Should fail with ExecutionException");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
            Assert.assertTrue(cause.toString().contains("Failed to connect"));
        }
    }
}
