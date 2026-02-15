/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler;
import io.strimzi.kafka.oauth.server.OAuthSaslAuthenticationException;
import io.strimzi.kafka.oauth.server.ServerConfig;
import io.strimzi.kafka.oauth.services.ServiceException;
import io.strimzi.testsuite.oauth.MockOAuthTestEnvironment;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.kafka.oauth.common.IOUtil.randomHexString;
import static io.strimzi.testsuite.oauth.common.TestUtil.getRootCause;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;

/**
 * Tests for authorization endpoint authentication.
 * Validates that introspection and JWKS endpoints properly handle authentication with client credentials and bearer tokens.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthorizationEndpointsIT {

    private MockOAuthTestEnvironment environment;
    private OAuthBearerValidatorCallback[] oauthCallbacks;
    private String clientSrv;
    private String clientSrvSecret;
    private String clientSrvBearerToken;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() throws Exception {
        environment = new MockOAuthTestEnvironment();
        environment.start();

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
        String projectRoot = Common.getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                projectRoot + "/../docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        // Login with client app's client_id + secret to obtain an access token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create("https://" + Common.getMockOAuthAuthHostPort() + "/token"),
                sslFactory,
                SSLUtil.createAnyHostHostnameVerifier(),
                clientApp,
                clientAppSecret,
                true,
                null,
                null,
                true);

        oauthCallbacks = new OAuthBearerValidatorCallback[]{new OAuthBearerValidatorCallback(tokenInfo.token())};
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @DisplayName("Introspection endpoint should handle authentication with client credentials and bearer tokens")
    @Tag("auth")
    void testIntrospectEndpointAuthentication() throws IOException, UnsupportedCallbackException {
        // introspect with clientid + secret

        //     set mock auth server introspection endpoint mode - by default authentication is required by appserver
        changeAuthServerMode("introspect", "MODE_200");
        changeAuthServerMode("userinfo", "MODE_200");

        //     configure validator with wrong client_id + secret
        Map<String, String> attrs = new HashMap<>();

        //attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id-introspect");
        attrs.put(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI, "https://" + Common.getMockOAuthAuthHostPort() + "/introspect");
        attrs.put(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI, "https://" + Common.getMockOAuthAuthHostPort() + "/userinfo");
        attrs.put(ServerConfig.OAUTH_USERNAME_CLAIM, "uid");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "bad-client-id");
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, "bad-client-secret");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://mockoauth:8090");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
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
            Assertions.assertTrue(cause.getMessage().contains("introspect failed with status 401"), "Error message check");
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
            Assertions.assertTrue(cause.getMessage().contains("introspect failed with status 401"), "Error message check");
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
    @DisplayName("JWKS endpoint should handle authentication with client credentials and bearer tokens")
    @Tag("auth")
    @Tag("jwt")
    @Tag("jwks")
    void testJwksEndpointAuthentication() throws IOException, UnsupportedCallbackException {
        // jwks with clientid + secret

        //     set mock auth server jwks endpoint mode - by default NO authentication is required by appserver
        changeAuthServerMode("jwks", "MODE_200_PROTECTED");

        //     configure validator with wrong client_id + secret
        Map<String, String> attrs = new HashMap<>();

        //attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id-jwks");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "bad-client-id");
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, "bad-client-secret");
        attrs.put(ServerConfig.OAUTH_JWKS_ENDPOINT_URI, "https://" + Common.getMockOAuthAuthHostPort() + "/jwks");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://mockoauth:8090");
        attrs.put(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, "false");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        attrs.put(ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        JaasServerOauthValidatorCallbackHandler handler;
        try {
            reconfigureHandler(attrs);
        } catch (Exception exception) {
            Assertions.assertEquals(ServiceException.class, exception.getClass(), "Exception is ServiceException");
            Assertions.assertTrue(exception.getMessage().contains("Failed to fetch public keys"), "Error message check");
            Throwable cause = exception.getCause();
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage().contains("jwks failed with status 401"), "Cause message check");

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
            Assertions.assertTrue(exception.getMessage().contains("Failed to fetch public keys"), "Error message check");
            Throwable cause = exception.getCause();
            Assertions.assertEquals(HttpException.class, cause.getClass(), "Cause is HttpException");
            Assertions.assertTrue(cause.getMessage().contains("jwks failed with status 401"), "Cause message check");
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

    @NotNull
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
