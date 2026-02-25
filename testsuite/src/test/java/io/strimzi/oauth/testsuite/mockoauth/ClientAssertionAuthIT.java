/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.WWW_FORM_CONTENT_TYPE;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestHostnameVerifier;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestSSLFactory;

/**
 * Tests for client assertion authentication.
 * Validates that client_assertion authentication works correctly with JWT bearer tokens.
 */
@OAuthEnvironment(authServer = AuthServer.MOCK_OAUTH)
public class ClientAssertionAuthIT {

    private static final Logger log = LoggerFactory.getLogger(ClientAssertionAuthIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.AUTH)
    @Tag(TestTags.CLIENT_ASSERTION)
    void testClientAssertionAuthentication() throws Exception {

        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "token", "MODE_200");
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "introspect", "MODE_200");
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "jwks", "MODE_200");

        // create a client for resource server
        String clientSrv = "appserver";
        String clientSrvSecret = "appserver-secret";
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), clientSrv, clientSrvSecret);

        // create a client client2
        String client2 = "client2";
        String client2Assertion = "client2-assertion";
        MockOAuthAdmin.createOAuthClientWithAssertion(env.getMockOAuthAdminHostPort(), client2, client2Assertion);

        SSLSocketFactory sslFactory = createTestSSLFactory();
        HostnameVerifier hostnameVerifier = createTestHostnameVerifier();

        try {
            // Use client_credentials to authenticate with wrong client_assertion
            TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientAssertion(
                    URI.create("https://" + env.getMockOAuthHostPort() + "/token"),
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
                URI.create("https://" + env.getMockOAuthHostPort() + "/token"),
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
        ObjectNode json = HttpUtil.post(URI.create("https://" + env.getMockOAuthHostPort() + "/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertTrue(json.get("active").asBoolean(), "Token active");
        Assertions.assertEquals(client2, json.get("client_id") != null ? json.get("client_id").asText() : null, "Introspection endpoint response contains `client_id`");
        Assertions.assertNull(json.get("username"), "Introspection endpoint response does not contain `username`");
    }
}
