/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.Assert;

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;


public class JaasClientConfigTest {

    private static final String KAFKA_BOOTSTRAP = "kafka:9092";
    private static final String TOKEN_ENDPOINT_URI = "https://mockoauth:8090/token";
    private static final String KAFKA_PRODUCER_CLIENT = "kafka-producer-client";
    private static final String KAFKA_PRODUCER_CLIENT_SECRET = "kafka-producer-client-secret";
    private static final String KAFKA_CLI = "kafka-cli";
    private static final String KAFKA_USER = "kafka-user";
    private static final String KAFKA_USER_PASSWORD = "kafka-user-password";

    public void doTest() throws Exception {

        testValidConfigurations();

        testNoAccessTokenAndNoTokenEndpoint();

        testNoClientId();

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "client credentials");
        }

        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "no password specified");
        }

        // Fix it, now it should try to authenticate with mockoauth server
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed due to missing truststore");

        } catch (KafkaException e) {
            assertLoginException(e);
        }
    }

    private void testNoClientId() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified (OAUTH_CLIENT_ID)");
        }

        // has username but no password
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified (OAUTH_CLIENT_ID)");
        }

        // add password, still has no client id
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified (OAUTH_CLIENT_ID)");
        }
    }

    private void testNoAccessTokenAndNoTokenEndpoint() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        // no token endpoint
        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access Token not specified");
        }

        // fix it by adding token endpoint
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed due to missing truststore");

        } catch (KafkaException e) {
            assertLoginException(e);
        }
    }

    private void testValidConfigurations() {

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "sometoken");
        oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, "sometoken");
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            assertExecutionException(e);
        }

        // Still valid config
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            assertExecutionException(e);
        }
    }

    private void assertExecutionException(Throwable e) {
        Throwable cause = e.getCause();
        Assert.assertEquals("is a ExecutionException", ExecutionException.class, e.getClass());

        Assert.assertTrue("Failed to parse token error", cause.getMessage().contains("Failed to parse JWT"));
    }

    private void assertConfigException(Throwable e, String message) {
        Throwable cause = e.getCause();
        Assert.assertEquals("is a KafkaException", KafkaException.class, e.getClass());

        Throwable nestedCause = cause.getCause();
        Assert.assertEquals("is a ConfigException", ConfigException.class, nestedCause.getClass());
        Assert.assertTrue("Contains '" + message + "'", nestedCause.getMessage().contains(message));
    }

    private void assertLoginException(Throwable e) {
        Throwable cause = e.getCause();
        Assert.assertEquals("is a KafkaException", KafkaException.class, e.getClass());

        Throwable nestedCause = cause.getCause();
        Assert.assertEquals("is a LoginException", LoginException.class, nestedCause.getClass());
    }

    private void initJaas(Map<String, String> oauthConfig) throws Exception {
        Properties producerProps = Common.buildProducerConfigOAuthBearer(KAFKA_BOOTSTRAP, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>("Test-testTopic", "The Message")).get();
        }
    }

}
