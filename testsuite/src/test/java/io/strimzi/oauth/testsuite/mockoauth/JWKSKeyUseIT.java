/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;
import io.strimzi.kafka.oauth.validator.TokenValidationException;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.HashMap;

import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getProjectRoot;

/**
 * Tests for JWKS key use attribute handling.
 * Validates that JWT signature validation properly handles the 'use' attribute in JWKS keys.
 */
@OAuthEnvironment(authServer = AuthServer.MOCK_OAUTH)
public class JWKSKeyUseIT {
    private static final Logger log = LoggerFactory.getLogger(JWKSKeyUseIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Token validation should fail when JWKS key lacks 'use' attribute and enforcement is enabled")
    @Tag(TestTags.JWT)
    @Tag(TestTags.JWKS)
    void testJWKSKeyUseEnforcement() throws Exception {
        Services.configure(new HashMap<>());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITHOUT_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        String projectRoot = getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
            projectRoot + "/docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        JWTSignatureValidator validator = createTokenValidator("enforceKeyUse", sslFactory, false);

        // Get a new token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            SSLUtil.createAnyHostHostnameVerifier(),
            testClient,
            testSecret,
            true,
            null,
            null,
            true);

        TokenIntrospection.debugLogJWT(log, tokenInfo.token());

        // Try to validate it - should fail because key lacks 'use' attribute
        try {
            validator.validate(tokenInfo.token());
            Assertions.fail("Token validation should fail when key lacks 'use' attribute");
        } catch (TokenValidationException ignored) {
            // Expected - validation should fail
        }
    }

    @Test
    @DisplayName("Token validation should succeed when ignoreKeyUse is enabled")
    @Tag(TestTags.JWT)
    @Tag(TestTags.JWKS)
    void testJWKSKeyUseIgnored() throws Exception {
        Services.configure(new HashMap<>());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITHOUT_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        String projectRoot = getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
            projectRoot + "/docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        // Get a new token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            SSLUtil.createAnyHostHostnameVerifier(),
            testClient,
            testSecret,
            true,
            null,
            null,
            true);

        TokenIntrospection.debugLogJWT(log, tokenInfo.token());

        // Create validator with ignoreKeyUse=true
        JWTSignatureValidator validatorIgnoreKeyUse = createTokenValidator("ignoreKeyUse", sslFactory, true);

        // Try to validate the token - should pass
        validatorIgnoreKeyUse.validate(tokenInfo.token());
    }

    private static JWTSignatureValidator createTokenValidator(String validatorId, SSLSocketFactory sslFactory, boolean ignoreKeyUse) {
        return new JWTSignatureValidator(validatorId,
            null,
            null,
            null,
            "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/jwks",
            sslFactory,
            SSLUtil.createAnyHostHostnameVerifier(),
            new PrincipalExtractor(),
            null,
            null,
            "https://mockoauth:8090",
            30,
            0,
            300,
            ignoreKeyUse,
            false,
            null,
            null,
            60,
            60,
            true,
            true,
            true);
    }
}
