/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import java.util.HashMap;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenIntrospection;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getProjectRoot;
import static io.strimzi.testsuite.oauth.mockoauth.Common.loginWithClientSecretAndExtraAttrs;

public class JwtExtractTest {

    private static final Logger log = LoggerFactory.getLogger(JWKSKeyUseTest.class);

    public void doTest() throws Exception {
        testExpiresAtOverflow();
    }

    public void testExpiresAtOverflow() throws Exception {
        Services.configure(Collections.emptyMap());

        changeAuthServerMode("jwks", "MODE_JWKS_RSA_WITH_SIG_USE");
        changeAuthServerMode("token", "MODE_200");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        String projectRoot = getProjectRoot();
        String trustStorePath = projectRoot + "/../docker/certificates/ca-truststore.p12";
        String trustStorePass = "changeit";

        SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(
                trustStorePath, null, trustStorePass, null, null);

        JWTSignatureValidator validator = createTokenValidator("enforceKeyUse", sslFactory, false);

        Map<String, String> extraAttrs = new HashMap<>();
        extraAttrs.put("EXP", Long.toString(Integer.MAX_VALUE + 1L));
        String extraAttrsString = extraAttrs.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + urlencode(entry.getValue()))
                .collect(Collectors.joining("&"));

        // Now get a new token
        String accessToken = loginWithClientSecretAndExtraAttrs(
                "https://mockoauth:8090/token",
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
