/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;

import static io.strimzi.testsuite.oauth.mockoauth.Common.WWW_FORM_CONTENT_TYPE;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClientWithAssertion;

/**
 * Tests for client assertion authentication.
 * Validates that client_assertion authentication works correctly with JWT bearer tokens.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClientAssertionAuthIT {

    private static final Logger log = LoggerFactory.getLogger(ClientAssertionAuthIT.class);

    private MockOAuthTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new MockOAuthTestEnvironment();
        environment.start();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @DisplayName("Client assertion authentication should work with correct assertion and fail with incorrect one")
    @Tag("auth")
    @Tag("client-assertion")
    void testClientAssertionAuthentication() throws Exception {

        changeAuthServerMode("token", "MODE_200");
        changeAuthServerMode("introspect", "MODE_200");
        changeAuthServerMode("jwks", "MODE_200");

        // create a client for resource server
        String clientSrv = "appserver";
        String clientSrvSecret = "appserver-secret";
        createOAuthClient(clientSrv, clientSrvSecret);

        // create a client client2
        String client2 = "client2";
        String client2Assertion = "client2-assertion";
        createOAuthClientWithAssertion(client2, client2Assertion);

        String projectRoot = Common.getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                projectRoot + "/docker/certificates/ca-truststore.p12", null, "changeit", null, null);
        HostnameVerifier hostnameVerifier = SSLUtil.createAnyHostHostnameVerifier();

        try {
            // Use client_credentials to authenticate with wrong client_assertion
            TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientAssertion(
                    URI.create("https://" + Common.getMockOAuthAuthHostPort() + "/token"),
                    sslFactory,
                    hostnameVerifier,
                    client2,
                    "bad-client-assertion",
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    true,
                    null,
                    null,
                    null);

            Assertions.fail("Should have failed with 401");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof HttpException, "Cause is HttpException");
            Assertions.assertEquals(401, ((HttpException) cause).getStatus(), "Expected status 401");
        }

        // Use client_credentials to authenticate with correct client_assertion
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientAssertion(
                URI.create("https://" + Common.getMockOAuthAuthHostPort() + "/token"),
                sslFactory,
                hostnameVerifier,
                client2,
                client2Assertion,
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                true,
                null,
                null,
                null);

        String token = tokenInfo.token();
        Assertions.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        // introspect the token using the introspection endpoint
        ObjectNode json = HttpUtil.post(URI.create("https://" + Common.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertTrue(json.get("active").asBoolean(), "Token active");
        Assertions.assertEquals(client2, json.get("client_id") != null ? json.get("client_id").asText() : null, "Introspection endpoint response contains `client_id`");
        Assertions.assertNull(json.get("username"), "Introspection endpoint response does not contain `username`");
    }
}
