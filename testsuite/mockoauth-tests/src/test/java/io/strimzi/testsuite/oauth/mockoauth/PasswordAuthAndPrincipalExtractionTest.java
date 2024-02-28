/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

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
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.text.ParseException;

import static io.strimzi.testsuite.oauth.mockoauth.Common.WWW_FORM_CONTENT_TYPE;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthUser;
import static io.strimzi.testsuite.oauth.mockoauth.Common.revokeToken;

public class PasswordAuthAndPrincipalExtractionTest {

    private static final Logger log = LoggerFactory.getLogger(PasswordAuthAndPrincipalExtractionTest.class);

    public void doTest() throws Exception {

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

        String projectRoot = Common.getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                projectRoot + "/../docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        PrincipalExtractor principalExtractor = new PrincipalExtractor("username", "pref_", "clientId", "pref_service-account-");

        // authenticate user against token endpoint with the correct password
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithPassword(
                URI.create("https://mockoauth:8090/token"),
                sslFactory,
                null,
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
        Assert.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        String principal = principalExtractor.getPrincipal(JSONUtil.readJSON(getAccessTokenPayload(token), JsonNode.class));
        Assert.assertEquals("Principal should be: 'pref_" + user1 + "'", "pref_" + user1, principal);


        ObjectNode json;

        // introspect the token using the introspection endpoint
        json = HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assert.assertTrue("Token active", json.get("active").asBoolean());
        Assert.assertEquals("Introspection endpoint response contains `username`", user1, json.get("username") != null ? json.get("username").asText() : null);
        Assert.assertEquals("Introspection endpoint response contains `client_id`", client1, json.get("client_id") != null ? json.get("client_id").asText() : null);

        // revoke the token
        revokeToken(token);

        // introspect the token again
        json = HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assert.assertFalse("Token not active", json.get("active").asBoolean());

        // introspect an invalid token
        json = HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=invalidtoken", ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assert.assertFalse("Token not active", json.get("active").asBoolean());

        // introspect the token using the introspection endpoint with a bad secret
        try {
            HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                    "Basic " + OAuthAuthenticator.base64encode(clientSrv + ":bad"), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

            Assert.fail("Should have failed with 401");
        } catch (HttpException e) {
            Assert.assertEquals("Expected status 401", 401, e.getStatus());
        }

        // Use client_credentials to authenticate
        tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create("https://mockoauth:8090/token"),
                sslFactory,
                null,
                client1,
                client1Secret,
                true,
                null,
                null,
                null,
                true);

        token = tokenInfo.token();
        Assert.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        principal = principalExtractor.getPrincipal(JSONUtil.readJSON(getAccessTokenPayload(token), JsonNode.class));
        Assert.assertEquals("Principal should be: 'pref_service-account-" + client1 + "'", "pref_service-account-" + client1, principal);

        // introspect the token using the introspection endpoint
        json = HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assert.assertTrue("Token active", json.get("active").asBoolean());
        Assert.assertEquals("Introspection endpoint response contains `client_id`", client1, json.get("client_id") != null ? json.get("client_id").asText() : null);
        Assert.assertNull("Introspection endpoint response does not contain `username`", json.get("username"));
    }

    private String getAccessTokenPayload(String token) throws ParseException {
        JWSObject jws = JWSObject.parse(token);
        return jws.getPayload().toString();
    }
}
