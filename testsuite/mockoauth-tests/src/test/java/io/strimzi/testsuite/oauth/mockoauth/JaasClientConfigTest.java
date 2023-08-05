/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler;
import io.strimzi.kafka.oauth.common.ConfigException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Arrays;
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

        testMissingClientSecret();

        testMissingPassword();

        testMissingTrustStore();

        testAllConfigOptions();
    }

    private void testAllConfigOptions() throws IOException {

        JaasClientOauthLoginCallbackHandler loginHandler = new JaasClientOauthLoginCallbackHandler();

        Map<String, String> attrs = new HashMap<>();
        attrs.put(ClientConfig.OAUTH_CONFIG_ID, "config-id");
        attrs.put(ClientConfig.OAUTH_REFRESH_TOKEN, "refresh-token");
        attrs.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, "https://sso/token");
        attrs.put(ClientConfig.OAUTH_CLIENT_ID, "client-id");
        attrs.put(ClientConfig.OAUTH_CLIENT_SECRET, "client-secret");
        attrs.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, "username");
        attrs.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, "password");
        attrs.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username-claim");
        attrs.put(ClientConfig.OAUTH_FALLBACK_USERNAME_CLAIM, "fallback-username-claim");
        attrs.put(ClientConfig.OAUTH_FALLBACK_USERNAME_PREFIX, "username-prefix");
        attrs.put(ClientConfig.OAUTH_SCOPE, "scope");
        attrs.put(ClientConfig.OAUTH_AUDIENCE, "audience");
        attrs.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");
        attrs.put(ClientConfig.OAUTH_MAX_TOKEN_EXPIRY_SECONDS, "300");
        attrs.put(ClientConfig.OAUTH_CONNECT_TIMEOUT_SECONDS, "20");
        attrs.put(ClientConfig.OAUTH_READ_TIMEOUT_SECONDS, "25");
        attrs.put(ClientConfig.OAUTH_HTTP_RETRIES, "3");
        attrs.put(ClientConfig.OAUTH_HTTP_RETRY_PAUSE_MILLIS, "500");
        attrs.put(ClientConfig.OAUTH_ENABLE_METRICS, "true");
        attrs.put(ClientConfig.OAUTH_INCLUDE_ACCEPT_HEADER, "false");


        AppConfigurationEntry jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);


        Map<String, String> clientProps = new HashMap<>();
        clientProps.put("security.protocol", "SASL_PLAINTEXT");
        clientProps.put("sasl.mechanism", "OAUTHBEARER");

        LogLineReader logReader = new LogLineReader(Common.LOG_PATH);
        logReader.readNext();

        loginHandler.configure(clientProps, "OAUTHBEARER", Arrays.asList(jaasConfig));

        Common.checkLog(logReader, "configId", "config-id",
            "refreshToken", "r\\*\\*",
            "tokenEndpointUri", "https://sso/token",
            "clientId", "client-id",
            "clientSecret", "c\\*\\*",
            "username", "username",
            "password", "p\\*\\*",
            "scope", "scope",
            "audience", "audience",
            "isJwt", "false",
            "maxTokenExpirySeconds", "300",
            "connectTimeout", "20",
            "readTimeout", "25",
            "retries", "3",
            "retryPauseMillis", "500",
            "enableMetrics", "true",
            "includeAcceptHeader", "false");

        // TODO: add usernameClaim, fallbackUserNameClaim and fallbackUserNamePrefix check when fixed
        //"principalExtractor", "PrincipalExtractor {usernameClaim: io.strimzi.kafka.oauth.common.PrincipalExtractor$Extractor@28486680, fallbackUsernameClaim: io.strimzi.kafka.oauth.common.PrincipalExtractor$Extractor@4d7e7435, fallbackUsernamePrefix: null}",


        // we could not check tokenEndpointUri and token in the same run

        attrs.put(ClientConfig.OAUTH_ACCESS_TOKEN, "access-token");
        jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);
        loginHandler = new JaasClientOauthLoginCallbackHandler();
        loginHandler.configure(clientProps, "OAUTHBEARER", Arrays.asList(jaasConfig));

        Common.checkLog(logReader, "token", "a\\*\\*");
    }

    @NotNull
    private void testMissingClientSecret() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "client credentials");
        }
    }

    private void testMissingPassword() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "no password specified");
        }
    }

    private void testMissingTrustStore() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
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
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }

        // has username but no password
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }

        // add password, still has no client id
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }
    }

    private void testNoAccessTokenAndNoTokenEndpoint() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
        }

        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
        }

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
        }

        // no token endpoint
        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assert.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token').");
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
