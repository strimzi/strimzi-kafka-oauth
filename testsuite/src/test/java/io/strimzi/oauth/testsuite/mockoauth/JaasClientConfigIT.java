/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.oauth.testsuite.logging.LogLineReader;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.utils.TestUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.utils.TestUtil.checkLogForRegex;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClientWithAssertion;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthUser;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithClientSecret;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernameForRefreshToken;

/**
 * Tests for JAAS client configuration validation and functionality.
 * These tests verify proper handling of OAuth configuration options,
 * error conditions, and token file locations.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        oauthProperties = {
            "oauth.config.id=JWT",
            "oauth.fail.fast=false",
            "oauth.jwks.endpoint.uri=https://mockoauth:8090/jwks",
            "oauth.jwks.refresh.seconds=10",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "oauth.check.access.token.type=false",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JaasClientConfigIT {

    private static final String KAFKA_PRODUCER_CLIENT = "kafka-producer-client";
    private static final String KAFKA_PRODUCER_CLIENT_SECRET = "kafka-producer-client-secret";
    private static final String KAFKA_CLI = "kafka-cli";
    private static final String KAFKA_USER = "kafka-user";
    private static final String KAFKA_USER_PASSWORD = "kafka-user-password";

    OAuthEnvironmentExtension env;

    private static String getTokenEndpointUri() {
        return "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token";
    }

    @Test
    @Order(1)
    @DisplayName("Test valid OAuth configurations")
    public void testValidConfigurations() {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "sometoken");
        oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, "sometoken");
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            assertExecutionException(e);
        }

        // Still valid config
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            assertExecutionException(e);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test missing access token and token endpoint")
    public void testNoAccessTokenAndNoTokenEndpoint() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        // no token endpoint
        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_PRODUCER_CLIENT);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, KAFKA_PRODUCER_CLIENT_SECRET);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "Access token not specified ('oauth.access.token'");
        }

        // fix it by adding token endpoint
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed due to missing truststore");

        } catch (KafkaException e) {
            assertLoginException(e);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test missing client ID")
    public void testNoClientId() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }

        // has username but no password
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }

        // add password, still has no client id
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "No client id specified ('oauth.client.id')");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test missing client secret")
    public void testMissingClientSecret() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "client credentials");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test missing password")
    public void testMissingPassword() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertConfigException(e, "no password specified");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Test missing truststore")
    public void testMissingTrustStore() throws Exception {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, KAFKA_CLI);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, KAFKA_USER);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, KAFKA_USER_PASSWORD);
        try {
            initJaas(oauthConfig);
            Assertions.fail("Should have failed due to missing truststore");

        } catch (KafkaException e) {
            assertLoginException(e);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Test all configuration options")
    public void testAllConfigOptions() throws IOException {

        JaasClientOauthLoginCallbackHandler loginHandler = new JaasClientOauthLoginCallbackHandler();

        Map<String, String> attrs = new HashMap<>();
        attrs.put(ClientConfig.OAUTH_CONFIG_ID, "config-id");
        attrs.put(ClientConfig.OAUTH_REFRESH_TOKEN, "refresh-token");
        attrs.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, "https://sso/token");
        attrs.put(ClientConfig.OAUTH_CLIENT_ID, "client-id");
        attrs.put(ClientConfig.OAUTH_CLIENT_SECRET, "client-secret");
        attrs.put(ClientConfig.OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE, "non-default-grant-type");
        attrs.put(ClientConfig.OAUTH_CLIENT_ASSERTION, "client-assertion");
        attrs.put(ClientConfig.OAUTH_CLIENT_ASSERTION_TYPE, "urn:ietf:params:oauth:client-assertion-type:saml2-bearer");
        attrs.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, "username");
        attrs.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, "password");
        attrs.put(ClientConfig.OAUTH_USERNAME_CLAIM, "username-claim");
        attrs.put(ClientConfig.OAUTH_FALLBACK_USERNAME_CLAIM, "fallback-username-claim");
        attrs.put(ClientConfig.OAUTH_FALLBACK_USERNAME_PREFIX, "fallback-username-prefix");
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
        attrs.put(ClientConfig.OAUTH_SASL_EXTENSION_PREFIX + "poolid", "poolid-value");
        attrs.put(ClientConfig.OAUTH_SASL_EXTENSION_PREFIX + "group.ref", "group-ref-value");


        AppConfigurationEntry jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);


        Map<String, String> clientProps = new HashMap<>();
        clientProps.put("security.protocol", "SASL_PLAINTEXT");
        clientProps.put("sasl.mechanism", "OAUTHBEARER");

        LogLineReader logReader = new LogLineReader("target/test.log");
        logReader.readNext();

        try {
            loginHandler.configure(clientProps, "OAUTHBEARER", Collections.singletonList(jaasConfig));
        } catch (Exception e) {
            Assertions.assertTrue(e instanceof ConfigException, "Is a ConfigException");
            Assertions.assertTrue(e.getMessage()
                .contains("Invalid sasl extension key: 'group.ref'"), "Invalid sasl extension key: " + e.getMessage());
        }

        logReader.readNext();

        attrs.remove(ClientConfig.OAUTH_SASL_EXTENSION_PREFIX + "group.ref");
        attrs.put(ClientConfig.OAUTH_SASL_EXTENSION_PREFIX + "group", "group-ref-value");

        loginHandler.configure(clientProps, "OAUTHBEARER", Collections.singletonList(jaasConfig));

        MockOAuthAdmin.checkLog(logReader, "configId", "config-id",
            "refreshToken", "r\\*\\*",
            "tokenEndpointUri", "https://sso/token",
            "clientId", "client-id",
            "clientSecret", "c\\*\\*",
            "grantType", "non-default-grant-type",
            "clientAssertion", "c\\*\\*",
            "clientAssertionType", "urn:ietf:params:oauth:client-assertion-type:saml2-bearer",
            "username", "username",
            "password", "p\\*\\*",
            "scope", "scope",
            "audience", "audience",
            "isJwt", "false",
            "usernameClaim", "username-claim",
            "fallbackUsernameClaim", "fallback-username-claim",
            "fallbackUsernamePrefix", "username-prefix",
            "maxTokenExpirySeconds", "300",
            "connectTimeout", "20",
            "readTimeout", "25",
            "retries", "3",
            "retryPauseMillis", "500",
            "enableMetrics", "true",
            "includeAcceptHeader", "false",
            "saslExtensions", "\\{poolid=poolid-value, group=group-ref-value\\}");


        // we could not check tokenEndpointUri and token in the same run

        attrs.put(ClientConfig.OAUTH_ACCESS_TOKEN, "access-token");


        // check token locations and client assertion

        final Path accessTokenPath = Paths.get("/tmp/access-token");
        final Path refreshTokenPath = Paths.get("/tmp/refresh-token");
        final Path clientAssertionPath = Paths.get("/tmp/client-assertion");

        attrs.put(ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION, accessTokenPath.toString());
        attrs.put(ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION, refreshTokenPath.toString());
        attrs.put(ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION, clientAssertionPath.toString());
        attrs.remove(ClientConfig.OAUTH_CLIENT_ASSERTION_TYPE);

        jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);
        loginHandler = new JaasClientOauthLoginCallbackHandler();

        try {
            loginHandler.configure(clientProps, "OAUTHBEARER", Collections.singletonList(jaasConfig));
        } catch (ConfigException e) {
            Assertions.assertTrue(e.getMessage()
                .contains("Specified access token location is invalid"), "location is invalid");
        }

        createFiles(accessTokenPath,
            refreshTokenPath,
            clientAssertionPath);
        try {
            loginHandler.configure(clientProps, "OAUTHBEARER", Collections.singletonList(jaasConfig));

            MockOAuthAdmin.checkLog(logReader, "token", "a\\*\\*",
                "tokenLocation", accessTokenPath.toString(),
                "refreshTokenLocation", refreshTokenPath.toString(),
                "clientAssertionLocation", clientAssertionPath.toString(),
                "clientAssertionType", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
            );
        } finally {
            deleteFiles(accessTokenPath,
                refreshTokenPath,
                clientAssertionPath);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Test SASL extensions configuration")
    public void testSaslExtensions() throws Exception {
        String testClient = "testclient";
        String testSecret = "testsecret";

        changeAuthServerMode("jwks", "mode_200");
        changeAuthServerMode("token", "mode_200");
        createOAuthClient(testClient, testSecret);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, testClient);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, testSecret);
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        oauthConfig.put(ClientConfig.OAUTH_SASL_EXTENSION_PREFIX + "extoption", "optionvalue");

        LogLineReader logReader = new LogLineReader("target/test.log");
        logReader.readNext();

        // If it fails with 'Unknown signing key' it means that Kafka has not managed to load JWKS keys yet
        // due to jwks endpoint returning status 404 initially
        initJaasWithRetry(oauthConfig);

        List<String> lines = logReader.readNext();
        // Check in the log that SASL extensions have been properly set
        checkLogForRegex(lines, ".*LoginManager.*extensionsMap=\\{extoption=optionvalue\\}.*");
    }

    @Test
    @Order(9)
    @DisplayName("Test access token location from file")
    public void testAccessTokenLocation() throws Exception {

        String testClient = "testclient";
        String testSecret = "testsecret";

        changeAuthServerMode("jwks", "mode_200");
        changeAuthServerMode("token", "mode_200");
        createOAuthClient(testClient, testSecret);

        String accessToken = loginWithClientSecret(getTokenEndpointUri(), testClient, testSecret, "target/kafka/certs/ca-truststore.p12", "changeit");

        Path accessTokenFilePath = Paths.get("target/access_token_file");
        Files.write(accessTokenFilePath, accessToken.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            LogLineReader logReader = new LogLineReader("target/test.log");
            logReader.readNext();

            Map<String, String> oauthConfig = new HashMap<>();
            oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "token-should-be-ignored");
            oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_LOCATION, accessTokenFilePath.toString());

            // If it fails with 'Unknown signing key' it means that Kafka has not managed to load JWKS keys yet
            // due to jwks endpoint returning status 404 initially
            initJaasWithRetry(oauthConfig);

            List<String> lines = logReader.readNext();
            boolean found = checkLogForRegex(lines, "should only give access to owner");
            Assertions.assertTrue(found, "should see access permissions issue warning in log");

            found = checkLogForRegex(lines, "access token will be ignored");
            Assertions.assertTrue(found, "should see access token ignored message in log");

            Files.delete(accessTokenFilePath);


            // recreate a token file with user-private visibility
            createPrivateFile(accessTokenFilePath);
            Files.write(accessTokenFilePath, accessToken.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

            initJaas(oauthConfig);

            lines = logReader.readNext();
            found = checkLogForRegex(lines, "should only give access to owner");

            Assertions.assertFalse(found, "should NOT see access permissions issue warning in log");

        } finally {
            Files.delete(accessTokenFilePath);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Test refresh token location from file")
    public void testRefreshTokenLocation() throws Exception {

        String pubClient = "pubClient";

        String testUser = "testUser";
        String testPassword = "testPassword";

        changeAuthServerMode("jwks", "mode_200");
        changeAuthServerMode("token", "mode_200");
        createOAuthClient(pubClient, "");
        createOAuthUser(testUser, testPassword);

        String refreshToken = loginWithUsernameForRefreshToken(getTokenEndpointUri(), testUser, testPassword, pubClient, "target/kafka/certs/ca-truststore.p12", "changeit");

        Path refreshTokenFilePath = Paths.get("target/refresh_token_file");
        Files.write(refreshTokenFilePath, refreshToken.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        try {
            LogLineReader logReader = new LogLineReader("target/test.log");
            logReader.readNext();

            Map<String, String> oauthConfig = new HashMap<>();
            oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, pubClient);
            oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, "token-should-be-ignored");
            oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN_LOCATION, refreshTokenFilePath.toString());
            oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());

            String truststoreLocation = TestUtil.getProjectRoot() + "/docker/certificates/ca-truststore.p12";
            oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, truststoreLocation);
            oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
            oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

            // If it fails with 'Unknown signing key' it means that Kafka has not managed to load JWKS keys yet
            // due to jwks endpoint returning status 404 initially
            initJaasWithRetry(oauthConfig);

            List<String> lines = logReader.readNext();
            boolean found = checkLogForRegex(lines, "should only give access to owner");
            Assertions.assertTrue(found, "should see access permissions issue warning in log");

            found = checkLogForRegex(lines, "refresh token will be ignored");
            Assertions.assertTrue(found, "should see refresh token ignored message in log");

            Files.delete(refreshTokenFilePath);


            // recreate a token file with user-private visibility
            createPrivateFile(refreshTokenFilePath);
            Files.write(refreshTokenFilePath, refreshToken.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

            initJaas(oauthConfig);

            lines = logReader.readNext();
            found = checkLogForRegex(lines, "should only give access to owner");

            Assertions.assertFalse(found, "should NOT see access permissions issue warning in log");

        } finally {
            Files.delete(refreshTokenFilePath);
        }
    }

    @Test
    @Order(11)
    @DisplayName("Test client assertion location from file")
    public void testClientAssertionLocation() throws Exception {

        String testClient = "clientWithAssertion";
        String testAssertion = "client-assertion";

        changeAuthServerMode("jwks", "mode_200");
        changeAuthServerMode("token", "mode_200");
        createOAuthClientWithAssertion(testClient, testAssertion);

        Path clientAssertionFilePath = Paths.get("target/client_assertion_file");
        Files.write(clientAssertionFilePath, testAssertion.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        try {
            LogLineReader logReader = new LogLineReader("target/test.log");
            logReader.readNext();

            Map<String, String> oauthConfig = new HashMap<>();
            oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, testClient);
            oauthConfig.put(ClientConfig.OAUTH_CLIENT_ASSERTION, "token-should-be-ignored");
            oauthConfig.put(ClientConfig.OAUTH_CLIENT_ASSERTION_LOCATION, clientAssertionFilePath.toString());
            oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());

            String truststoreLocation = TestUtil.getProjectRoot() + "/docker/certificates/ca-truststore.p12";
            oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, truststoreLocation);
            oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
            oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

            // If it fails with 'Unknown signing key' it means that Kafka has not managed to load JWKS keys yet
            // due to jwks endpoint returning status 404 initially
            initJaasWithRetry(oauthConfig);

            List<String> lines = logReader.readNext();
            boolean found = checkLogForRegex(lines, "should only give access to owner");
            Assertions.assertTrue(found, "should see access permissions issue warning in log");

            found = checkLogForRegex(lines, "client assertion will be ignored");
            Assertions.assertTrue(found, "should see client assertion ignored message in log");

            Files.delete(clientAssertionFilePath);


            // recreate a token file with user-private visibility
            createPrivateFile(clientAssertionFilePath);
            Files.write(clientAssertionFilePath, testAssertion.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

            initJaas(oauthConfig);

            lines = logReader.readNext();
            found = checkLogForRegex(lines, "should only give access to owner");

            Assertions.assertFalse(found, "should NOT see access permissions issue warning in log");

        } finally {
            Files.delete(clientAssertionFilePath);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Test invalid grant type handling")
    public void testInvalidGrantType() throws Exception {
        String testClient = "testclient";
        String testSecret = "testsecret";

        changeAuthServerMode("jwks", "mode_200");
        changeAuthServerMode("token", "mode_200");
        createOAuthClient(testClient, testSecret);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, getTokenEndpointUri());
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, testClient);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, testSecret);
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        // Confirm fails with invalid grant type
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE, "dummy-grant-type");

        try {
            initJaasWithRetry(oauthConfig);
            Assertions.fail("Should have failed");

        } catch (KafkaException e) {
            assertLoginException(e);
        }

        // Confirm succeeds with valid grant type
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE, ClientConfig.OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE_DEFAULT_VALUE);

        LogLineReader logReader = new LogLineReader("target/test.log");
        logReader.readNext();

        initJaasWithRetry(oauthConfig);
        List<String> lines = logReader.readNext();
        boolean found = checkLogForRegex(lines, "Login succeeded");
        Assertions.assertTrue(found, "Login succeeded");

    }


    /**
     * If signing keys have not yet been loaded by kafka broker,
     * keep trying for up to 10 attempts with 2 second pause.
     *
     * @param oauthConfig The configuration
     * @throws Exception Any exception other than due to unknown signing key
     */
    private void initJaasWithRetry(Map<String, String> oauthConfig) throws Exception {
        Exception err;
        int tryCount = 0;

        do {
            tryCount++;
            try {
                initJaas(oauthConfig);
                return;
            } catch (Exception e) {
                err = e;
                String msg = e.getMessage();
                if (msg != null && msg.contains("Unknown signing key")) {
                    Thread.sleep(2000);
                } else {
                    throw e;
                }
            }
        } while (tryCount < 10);

        throw err;
    }

    private void assertExecutionException(Throwable e) {
        Throwable cause = e.getCause();
        Assertions.assertEquals(ExecutionException.class, e.getClass(), "is a ExecutionException");

        Assertions.assertTrue(cause.getMessage()
            .contains("Failed to parse JWT"), "Failed to parse token error");
    }

    private void assertConfigException(Throwable e, String message) {
        Throwable cause = e.getCause();
        Assertions.assertEquals(KafkaException.class, e.getClass(), "is a KafkaException");

        Throwable nestedCause = getRootCause(cause);
        Assertions.assertNotNull(nestedCause, "nestedCause not null");
        Assertions.assertEquals(ConfigException.class, nestedCause.getClass(), "is a ConfigException");

        String msg = nestedCause.getMessage();
        Assertions.assertTrue(msg != null && msg.contains(message), "Contains '" + message + "'");
    }

    private void assertLoginException(Throwable e) {
        Throwable cause = e.getCause();
        Assertions.assertEquals(KafkaException.class, e.getClass(), "is a KafkaException");

        Throwable nestedCause = getRootCause(cause);
        Assertions.assertEquals(LoginException.class, nestedCause == null ? null : nestedCause.getClass(), "is a LoginException");
    }

    private void initJaas(Map<String, String> oauthConfig) throws Exception {
        Properties producerProps = KafkaClientsConfig.buildProducerConfigOAuthBearer(env.getBootstrapServers(), oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>("Test-testTopic", "The Message"))
                .get();
        }
    }

    private void createFiles(Path... files) throws IOException {
        for (Path f : files) {
            Files.createFile(f);
        }
    }

    private void deleteFiles(Path... files) throws IOException {
        for (Path f : files) {
            Files.delete(f);
        }
    }

    private void createPrivateFile(Path file) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        Set<String> supportedViews = fs.supportedFileAttributeViews();
        if (supportedViews.contains("posix")) {
            FileAttribute<Set<PosixFilePermission>> fileAttrs = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));

            Files.createFile(file, fileAttrs);
        } else {
            throw new RuntimeException("Not a POSIX compatible filesystem: " + fs);
        }
    }
}
