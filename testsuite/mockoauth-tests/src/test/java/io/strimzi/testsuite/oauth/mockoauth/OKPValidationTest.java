/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.util.Collections;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createTokenValidator;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getProjectRoot;

public class OKPValidationTest {
    private static final Logger log = LoggerFactory.getLogger(OKPValidationTest.class);

    public void doTest() throws Exception {

        testOKPSignatureValidation();
    }

    /**
     * Test that JWTSignatureValidator correctly validates a token signed with an OKP (Ed25519) key
     * by the mock OAuth server, using the mock server's JWKS endpoint.
     *
     * This test requires the oauth-okp-support module to be available on the classpath.
     * If it's not available (i.e., when building without the okp-support profile), the test is skipped.
     */
    public void testOKPSignatureValidation() throws Exception {
        
        log.info("OKP support is available - running testOKPSignatureValidation");
        
        Services.configure(Collections.emptyMap());

        changeAuthServerMode("jwks", "MODE_JWKS_OKP_WITH_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        String projectRoot = getProjectRoot();
        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                projectRoot + "/../docker/certificates/ca-truststore.p12", null, "changeit", null, null);

        JWTSignatureValidator validator = createTokenValidator("okpValidator", sslFactory, false);

        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create("https://mockoauth:8090/token"),
                sslFactory,
                null,
                testClient,
                testSecret,
                true,
                null,
                null,
                true);

        TokenIntrospection.debugLogJWT(log, tokenInfo.token());

        validator.validate(tokenInfo.token());
    }
}