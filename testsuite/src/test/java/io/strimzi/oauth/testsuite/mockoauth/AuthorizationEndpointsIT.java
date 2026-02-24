/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler;
import io.strimzi.kafka.oauth.server.OAuthSaslAuthenticationException;
import io.strimzi.kafka.oauth.server.ServerConfig;
import io.strimzi.kafka.oauth.services.ServiceException;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.kafka.oauth.common.IOUtil.randomHexString;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestHostnameVerifier;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestSSLFactory;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;

/**
 * Tests for authorization endpoint authentication.
 * Validates that introspection and JWKS endpoints properly handle authentication with client credentials and bearer tokens.
 */
@OAuthEnvironment(authServer = AuthServer.MOCK_OAUTH)
public class AuthorizationEndpointsIT {

    OAuthEnvironmentExtension env;
    private OAuthBearerValidatorCallback[] oauthCallbacks;
    private String clientSrv;
    private String clientSrvSecret;
    private String clientSrvBearerToken;

    @BeforeAll
    void setUp() throws Exception {
        changeAuthServerMode("token", "MODE_200");

        // create a client for resource server
        clientSrv = "appserver";
        clientSrvSecret = "appserver-secret";
        createOAuthClient(clientSrv, clientSrvSecret);

        // create a bearer client
        String clientSrvBearer = "appserver-bearer";
        clientSrvBearerToken = randomHexString(32);
        createOAuthClient(clientSrvBearer, clientSrvBearerToken);

        // create a client for client app
        String clientApp = "client";
        String clientAppSecret = "client-secret";
        createOAuthClient(clientApp, clientAppSecret);

        // prepare TLS support
        SSLSocketFactory sslFactory = createTestSSLFactory();

        // Login with client app's client_id + secret to obtain an access token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            createTestHostnameVerifier(),
            clientApp,
            clientAppSecret,
            true,
            null,
            null,
            true);

        oauthCallbacks = new OAuthBearerValidatorCallback[]{new OAuthBearerValidatorCallback(tokenInfo.token())};
    }

    @Test
    @Tag(TestTags.AUTH)
    void testIntrospectEndpointAuthentication() throws IOException, UnsupportedCallbackException {
        // introspect with clientid + secret

        //     set mock auth server introspection endpoint mode - by default authentication is required by appserver
        changeAuthServerMode("introspect", "MODE_200");
        changeAuthServerMode("userinfo", "MODE_200");

        //     configure validator with wrong client_id + secret
        Map<String, String> attrs = new HashMap<>();

        //attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id-introspect");
        attrs.put(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI, "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/introspect");
        attrs.put(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI, "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/userinfo");
        attrs.put(ServerConfig.OAUTH_USERNAME_CLAIM, "uid");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "bad-client-id");
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, "bad-client-secret");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://mockoauth:8090");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        attrs.put(ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        JaasServerOauthValidatorCallbackHandler handler = reconfigureHandler(attrs);

        //     validate token
        try {
            handler.handle(oauthCallbacks);
            Assertions.fail("Should have failed");
        } catch (Exception exception) {
            Assertions.assertEquals(OAuthSaslAuthenticationException.class, exception.getClass(), "Exception is OAuthSaslAuthenticationException");
            Throwable cause = getRootCause(exception);
            Assertions.assertNotNull(cause, "Exception has a cause");
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage()
                .contains("introspect failed with status 401"), "Error message check");
        }
        handler.close();

        //     configure validator with correct client_id + secret
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, clientSrv);
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, clientSrvSecret);

        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();

        // introspect with bearer token
        //     configure validator with wrong bearer token
        attrs.remove(ServerConfig.OAUTH_CLIENT_ID);
        attrs.remove(ServerConfig.OAUTH_CLIENT_SECRET);
        attrs.put(ServerConfig.OAUTH_SERVER_BEARER_TOKEN, "bad-token");
        handler = reconfigureHandler(attrs);

        //     validate token
        try {
            handler.handle(oauthCallbacks);
            Assertions.fail("Should have failed");
        } catch (Exception exception) {
            Assertions.assertEquals(OAuthSaslAuthenticationException.class, exception.getClass(), "Exception is OAuthSaslAuthenticationException");
            Throwable cause = getRootCause(exception);
            Assertions.assertNotNull(cause, "Exception has a cause");
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage()
                .contains("introspect failed with status 401"), "Error message check");
        }
        handler.close();


        //     configure validator with correct bearer token
        attrs.put(ServerConfig.OAUTH_SERVER_BEARER_TOKEN, clientSrvBearerToken);
        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();


        // unprotected introspect
        changeAuthServerMode("introspect", "MODE_200_UNPROTECTED");

        //     configure validator without client_id or bearer token
        attrs.remove(ServerConfig.OAUTH_SERVER_BEARER_TOKEN);
        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();
    }

    @Test
    @Tag(TestTags.AUTH)
    @Tag(TestTags.JWT)
    @Tag(TestTags.JWKS)
    void testJwksEndpointAuthentication() throws IOException, UnsupportedCallbackException {
        // jwks with clientid + secret

        //     set mock auth server jwks endpoint mode - by default NO authentication is required by appserver
        changeAuthServerMode("jwks", "MODE_200_PROTECTED");

        //     configure validator with wrong client_id + secret
        Map<String, String> attrs = new HashMap<>();

        //attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id-jwks");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "bad-client-id");
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, "bad-client-secret");
        attrs.put(ServerConfig.OAUTH_JWKS_ENDPOINT_URI, "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/jwks");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://mockoauth:8090");
        attrs.put(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, "false");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        attrs.put(ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        JaasServerOauthValidatorCallbackHandler handler;
        try {
            reconfigureHandler(attrs);
        } catch (Exception exception) {
            Assertions.assertEquals(ServiceException.class, exception.getClass(), "Exception is ServiceException");
            Assertions.assertTrue(exception.getMessage()
                .contains("Failed to fetch public keys"), "Error message check");
            Throwable cause = exception.getCause();
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage()
                .contains("jwks failed with status 401"), "Cause message check");

        }

        //     configure validator with correct client_id + secret
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, clientSrv);
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, clientSrvSecret);

        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();

        // jwks with bearer token

        //     configure validator with wrong bearer token
        attrs.remove(ServerConfig.OAUTH_CLIENT_ID);
        attrs.remove(ServerConfig.OAUTH_CLIENT_SECRET);
        attrs.put(ServerConfig.OAUTH_SERVER_BEARER_TOKEN, "bad-token");

        try {
            reconfigureHandler(attrs);
        } catch (Exception exception) {
            Assertions.assertEquals(ServiceException.class, exception.getClass(), "Exception is ServiceException");
            Assertions.assertTrue(exception.getMessage()
                .contains("Failed to fetch public keys"), "Error message check");
            Throwable cause = exception.getCause();
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage()
                .contains("jwks failed with status 401"), "Cause message check");
        }

        //     configure validator with correct bearer token
        attrs.put(ServerConfig.OAUTH_SERVER_BEARER_TOKEN, clientSrvBearerToken);
        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();


        // unprotected jwks
        changeAuthServerMode("jwks", "MODE_200");

        //     configure validator without client_id or bearer token
        attrs.remove(ServerConfig.OAUTH_SERVER_BEARER_TOKEN);
        handler = reconfigureHandler(attrs);

        //     validate token
        handler.handle(oauthCallbacks);
        handler.close();
    }

    private static JaasServerOauthValidatorCallbackHandler reconfigureHandler(Map<String, String> attrs) {
        Map<String, String> serverProps = new HashMap<>();
        serverProps.put("security.protocol", "SASL_PLAINTEXT");
        serverProps.put("sasl.mechanism", "OAUTHBEARER");

        AppConfigurationEntry jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);
        JaasServerOauthValidatorCallbackHandler handler = new JaasServerOauthValidatorCallbackHandler();
        handler.configure(serverProps, "OAUTHBEARER", Collections.singletonList(jaasConfig));
        return handler;
    }
}
