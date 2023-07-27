/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;
import io.strimzi.kafka.oauth.validator.TokenValidationException;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.Collections;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getProjectRoot;

public class JWKSKeyUseTest {
    private static final Logger log = LoggerFactory.getLogger(PasswordAuthTest.class);

    public void doTest() throws Exception {
        Services.configure(Collections.emptyMap());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITHOUT_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        String projectRoot = getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                projectRoot + "/../docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        JWTSignatureValidator validator = createTokenValidator("enforceKeyUse", sslFactory, false);

        // Now get a new token
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create("https://mockoauth:8090/token"),
                sslFactory,
                null,
                testClient,
                testSecret,
                true,
                null,
                null,
                null);

        TokenIntrospection.debugLogJWT(log, tokenInfo.token());

        // and try to validate it
        // It should fail
        try {
            validator.validate(tokenInfo.token());
            Assert.fail("Token validation should fail");

        } catch (TokenValidationException ignored) {
        }

        // Repeat the test with `ignoreKeyUse: true`
        JWTSignatureValidator validatorIgnoreKeyUse = createTokenValidator("ignoreKeyUse", sslFactory, true);

        // Try to validate the token
        // It should pass
        validatorIgnoreKeyUse.validate(tokenInfo.token());
    }

    private static JWTSignatureValidator createTokenValidator(String validatorId, SSLSocketFactory sslFactory, boolean ignoreKeyUse) {
        return new JWTSignatureValidator(validatorId,
                "https://mockoauth:8090/jwks",
                sslFactory,
                null,
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