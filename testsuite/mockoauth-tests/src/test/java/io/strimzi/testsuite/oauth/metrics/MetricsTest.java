/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.metrics;

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
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getProjectRoot;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getPrometheusMetrics;
import static io.strimzi.testsuite.oauth.mockoauth.Common.reloadMetrics;

/**
 * Tests for OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with SASL OAUTHBEARER mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with SASL PLAIN mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
public class MetricsTest {

    static final Logger log = LoggerFactory.getLogger(MetricsTest.class);

    public void doTest() throws Exception {
        try {
            zeroCheck();

            postInitCheck();

            testNetworkErrors();

            testExpiredCert();

            testInternalServerErrors();

            testJwksIgnoreKeyUse();

        } catch (Throwable e) {
            log.error("Keycloak Authentication Test failed: ", e);
            throw e;
        }
    }

    private void testJwksIgnoreKeyUse() throws IOException {

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

    private void testInternalServerErrors() throws IOException, InterruptedException {

        // Configure jwks endpoint to return 503
        changeAuthServerMode("jwks", "mode_503");
        // Turn mockoauth auth server back on with cert no. 2
        changeAuthServerMode("server", "mode_cert_two_on");

        Thread.sleep(20000);
        Metrics metrics = reloadMetrics();


        // We should not see any 503 errors yet because of more TLS errors due to CERT_TWO being used
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertNull("There should be no 503 errors", value);
        // Switch mockoauth auth server back to using cert no. 1
        changeAuthServerMode("server", "mode_cert_one_on");

        Thread.sleep(20000);
        metrics = reloadMetrics();


        // We should see some 503 errors
        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some 503 errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some 503 errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void testExpiredCert() throws IOException, InterruptedException {

        // Turn mockoauth auth server back on, and make JWT produce SSL errors
        changeAuthServerMode("server", "mode_expired_cert_on");

        Thread.sleep(20000);
        Metrics metrics = reloadMetrics();


        // We should see some TLS errors
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "tls");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some TLS errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWT", "outcome", "error", "error_type", "tls");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some TLS errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void testNetworkErrors() throws IOException, InterruptedException {

        // Turn off mockoauth auth server to cause network errors
        changeAuthServerMode("server", "mode_off");

        Thread.sleep(20000);
        Metrics metrics = reloadMetrics();


        // See some network errors on JWT's
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "connect");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some network errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWT", "outcome", "error", "error_type", "connect");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some network errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void postInitCheck() throws IOException {
        Metrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        // mockoauth has JWKS endpoint configured to return 404
        // error counter for 404 for JWT should not be zero as at least one JWKS request should fail
        // during JWT listener's JWTSignatureValidator initialisation
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "404");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some 404 errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWT", "outcome", "error", "error_type", "http", "status", "404");
        Assert.assertNotNull("Metric missing", value);
        Assert.assertTrue("There should be some 404 errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void zeroCheck() throws IOException {
        Metrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        // assumption check
        // JWT listener config (on port 9404 in docker-compose.yml) has no token endpoint so the next metric should not exist
        String value = metrics.getValue("strimzi_oauth_validation_requests_count", "context", "JWT");
        Assert.assertNull(value);


        // initial checks - all success counters should be null
        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "INTROSPECT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);
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
                true);
    }
}
