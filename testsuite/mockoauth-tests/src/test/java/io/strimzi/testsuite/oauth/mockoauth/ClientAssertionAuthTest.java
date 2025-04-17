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
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;

import static io.strimzi.kafka.oauth.common.Common.OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE_FALLBACK;
import static io.strimzi.testsuite.oauth.mockoauth.Common.WWW_FORM_CONTENT_TYPE;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClientWithAssertion;

public class ClientAssertionAuthTest {

    private static final Logger log = LoggerFactory.getLogger(ClientAssertionAuthTest.class);

    public void doTest() throws Exception {

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
                projectRoot + "/../docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        try {
            // Use client_credentials to authenticate with wrong client_assertion
            TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientAssertion(
                    URI.create("https://mockoauth:8090/token"),
                    sslFactory,
                    null,
                    client2,
                    "bad-client-assertion",
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    true,
                    null,
                    null,
                    null,
                    OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE_FALLBACK);

            Assert.fail("Should have failed with 401");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("Cause is HttpException", cause instanceof HttpException);
            Assert.assertEquals("Expected status 401", 401, ((HttpException) cause).getStatus());
        }

        // Use client_credentials to authenticate with correct client_assertion
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientAssertion(
                URI.create("https://mockoauth:8090/token"),
                sslFactory,
                null,
                client2,
                client2Assertion,
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                true,
                null,
                null,
                null,
                OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE_FALLBACK);

        String token = tokenInfo.token();
        Assert.assertNotNull(token);

        TokenIntrospection.debugLogJWT(log, token);

        // introspect the token using the introspection endpoint
        ObjectNode json = HttpUtil.post(URI.create("https://mockoauth:8090/introspect"), sslFactory, null,
                "Basic " + OAuthAuthenticator.base64encode(clientSrv + ':' + clientSrvSecret), WWW_FORM_CONTENT_TYPE, "token=" + token, ObjectNode.class);

        log.info("Got introspection endpoint response: " + json);
        Assert.assertTrue("Token active", json.get("active").asBoolean());
        Assert.assertEquals("Introspection endpoint response contains `client_id`", client2, json.get("client_id") != null ? json.get("client_id").asText() : null);
        Assert.assertNull("Introspection endpoint response does not contain `username`", json.get("username"));
    }
}
