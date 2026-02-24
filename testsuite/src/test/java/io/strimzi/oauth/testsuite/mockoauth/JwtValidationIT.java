/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestHostnameVerifier;
import static io.strimzi.oauth.testsuite.utils.TestUtil.createTestSSLFactory;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getProjectRoot;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithClientSecretAndExtraAttrs;

/**
 * Tests for JWT validation edge cases:
 * - JWKS key 'use' attribute enforcement
 * - JWT 'exp' overflow handling
 */
@OAuthEnvironment(authServer = AuthServer.MOCK_OAUTH)
public class JwtValidationIT {
    private static final Logger log = LoggerFactory.getLogger(JwtValidationIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.JWT)
    @Tag(TestTags.JWKS)
    void testJWKSKeyUseEnforcement() throws Exception {
        Services.configure(new HashMap<>());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITHOUT_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        SSLSocketFactory sslFactory = createTestSSLFactory();

        JWTSignatureValidator validator = createTokenValidator("enforceKeyUse", sslFactory, false);

        // Get a new token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            createTestHostnameVerifier(),
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
    @Tag(TestTags.JWT)
    @Tag(TestTags.JWKS)
    void testJWKSKeyUseIgnored() throws Exception {
        Services.configure(new HashMap<>());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITHOUT_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        SSLSocketFactory sslFactory = createTestSSLFactory();

        // Get a new token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
            URI.create("https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token"),
            sslFactory,
            createTestHostnameVerifier(),
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

    @Test
    @Tag(TestTags.JWT)
    void testExpiresAtOverflow() throws Exception {
        Services.configure(new HashMap<>());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITH_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        SSLSocketFactory sslFactory = createTestSSLFactory();

        JWTSignatureValidator validator = createTokenValidator("enforceKeyUse", sslFactory, false);

        String projectRoot = getProjectRoot();
        String trustStorePath = projectRoot + "/docker/certificates/ca-truststore.p12";
        String trustStorePass = "changeit";

        Map<String, String> extraAttrs = new HashMap<>();
        extraAttrs.put("EXP", Long.toString(Integer.MAX_VALUE + 1L));
        String extraAttrsString = extraAttrs.entrySet()
            .stream()
            .map(entry -> entry.getKey() + "=" + urlencode(entry.getValue()))
            .collect(Collectors.joining("&"));

        // Now get a new token
        String accessToken = loginWithClientSecretAndExtraAttrs(
            "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/token",
            testClient,
            testSecret,
            trustStorePath,
            trustStorePass,
            extraAttrsString);

        TokenIntrospection.debugLogJWT(log, accessToken);

        // and try to validate it
        // The overflow bug triggers a TokenExpiredException
        validator.validate(accessToken);
    }

    private static JWTSignatureValidator createTokenValidator(String validatorId, SSLSocketFactory sslFactory, boolean ignoreKeyUse) {
        return new JWTSignatureValidator(validatorId,
            null,
            null,
            null,
            "https://" + MockOAuthAdmin.getMockOAuthAuthHostPort() + "/jwks",
            sslFactory,
            createTestHostnameVerifier(),
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
