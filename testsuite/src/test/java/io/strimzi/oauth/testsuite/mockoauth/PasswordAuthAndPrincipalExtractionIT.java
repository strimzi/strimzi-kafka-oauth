/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSObject;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.oauth.testsuite.utils.TestUtil;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.text.ParseException;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.WWW_FORM_CONTENT_TYPE;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthUser;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.revokeToken;

/**
 * Tests for password grant authentication and principal extraction.
 * Verifies OAuth password flow, token introspection, and principal extraction logic.
 */
@OAuthEnvironment(authServer = AuthServer.MOCK_OAUTH)
public class PasswordAuthAndPrincipalExtractionIT {

    private static final Logger log = LoggerFactory.getLogger(PasswordAuthAndPrincipalExtractionIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Test password grant authentication and principal extraction")
    public void testPasswordAuthAndPrincipalExtraction() throws Exception {
        changeAuthServerMode("token", "MODE_200");
        changeAuthServerMode("introspect", "MODE_200");

        // create a client for resource server
        String clientSrv = "appserver";
        String clientSrvSecret = "appserver-secret";
        createOAuthClient(clientSrv, clientSrvSecret);

        // create a client client1
        String client1 = "client1";
        String client1Secret = "client1-secret";
        createOAuthClient(client1, client1Secret);

        // create a user user1
        String user1 = "user1";
        String user1Pass = "user1-password";
        createOAuthUser(user1, user1Pass);

        String projectRoot = TestUtil.getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
            projectRoot + "/docker/certificates/ca-truststore.p12", null, "changeit", null, null);
        HostnameVerifier hostnameVerifier = SSLUtil.createAnyHostHostnameVerifier();

        PrincipalExtractor principalExtractor = new PrincipalExtractor("username", "pref_", "clientId", "pref_service-account-");

        // authenticate user against token endpoint with the correct password
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithPassword(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            hostnameVerifier,
            user1,
            user1Pass,
            client1,
            client1Secret,
            true,
            null,
            null,
            null,
            true);

        String token = tokenInfo.token();
        Assertions.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        String principal = principalExtractor.getPrincipal(JSONUtil.readJSON(getAccessTokenPayload(token), JsonNode.class));
        Assertions.assertEquals("pref_" + user1, principal, "Principal should be: 'pref_" + user1 + "'");


        ObjectNode json;

        // introspect the token using the introspection endpoint
        json = HttpUtil.post(URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, hostnameVerifier,
            "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertTrue(json.get("active")
            .asBoolean(), "Token active");
        Assertions.assertEquals(user1, json.get("username") != null ? json.get("username")
            .asText() : null, "Introspection endpoint response contains `username`");
        Assertions.assertEquals(client1, json.get("client_id") != null ? json.get("client_id")
            .asText() : null, "Introspection endpoint response contains `client_id`");

        // revoke the token
        revokeToken(token);

        // introspect the token again
        json = HttpUtil.post(URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, hostnameVerifier,
            "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertFalse(json.get("active")
            .asBoolean(), "Token not active");

        // introspect an invalid token
        json = HttpUtil.post(URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, hostnameVerifier,
            "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=invalidtoken", ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertFalse(json.get("active")
            .asBoolean(), "Token not active");

        // introspect the token using the introspection endpoint with a bad secret
        try {
            HttpUtil.post(URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, hostnameVerifier,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ":bad"), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

            Assertions.fail("Should have failed with 401");
        } catch (HttpException e) {
            Assertions.assertEquals(401, e.getStatus(), "Expected status 401");
        }

        // Use client_credentials to authenticate
        tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            null,
            client1,
            client1Secret,
            true,
            null,
            null,
            true);

        token = tokenInfo.token();
        Assertions.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        principal = principalExtractor.getPrincipal(JSONUtil.readJSON(getAccessTokenPayload(token), JsonNode.class));
        Assertions.assertEquals("pref_service-account-" + client1, principal, "Principal should be: 'pref_service-account-" + client1 + "'");

        // introspect the token using the introspection endpoint
        json = HttpUtil.post(URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect"), sslFactory, hostnameVerifier,
            "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assertions.assertTrue(json.get("active")
            .asBoolean(), "Token active");
        Assertions.assertEquals(client1, json.get("client_id") != null ? json.get("client_id")
            .asText() : null, "Introspection endpoint response contains `client_id`");
        Assertions.assertNull(json.get("username"), "Introspection endpoint response does not contain `username`");
    }

    private String getAccessTokenPayload(String token) throws ParseException {
        JWSObject jws = JWSObject.parse(token);
        return jws.getPayload()
            .toString();
    }
}
