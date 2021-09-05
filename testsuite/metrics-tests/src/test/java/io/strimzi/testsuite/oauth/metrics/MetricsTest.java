/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.metrics;

import io.strimzi.kafka.oauth.common.HttpUtil;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;

/**
 * Tests for OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with SASL OAUTHBEARER mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with SASL PLAIN mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
@RunWith(Arquillian.class)
public class MetricsTest {

    static final Logger log = LoggerFactory.getLogger(MetricsTest.class);

    @Test
    public void doTest() throws Exception {
        try {
            logStart("MetricsTest :: BasicTests");

            zeroCheck();

            postInitCheck();

            testNetworkErrors();

            testExpiredCert();

            testInternalServerErrors();


            //
            // Perform introspect endpoint tests
            //

            /*
            "strimzi_oauth_http_jwks_errornetworkcounter"
            "strimzi_oauth_http_jwks_errornetworkrequesttime"
            "strimzi_oauth_http_token_errornetworkcounter"
            "strimzi_oauth_http_token_errornetworkrequesttime"
            "strimzi_oauth_http_token_errornxxcounter"
            "strimzi_oauth_http_token_errornxxrequesttime"
            "strimzi_oauth_http_introspect_errornxxcounter"
            "strimzi_oauth_http_introspect_errornxxrequesttime"
            */

        } catch (Throwable e) {
            log.error("Keycloak Authentication Test failed: ", e);
            throw e;
        }
    }

    private void testInternalServerErrors() throws IOException, InterruptedException {

        // Configure jwks endpoint to return 503
        changeAuthServerMode("jwks", "mode_503");
        // Turn mockoauth auth server back on with cert no. 2
        changeAuthServerMode("server", "mode_cert_two_on");

        Thread.sleep(20000);
        Common.Metrics metrics = reloadMetrics();


        // We should not see any 503 errors yet because of more TLS errors due to CERT_TWO being used
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertNull("There should be no 503 errors", value);
        // Switch mockoauth auth server back to using cert no. 1
        changeAuthServerMode("server", "mode_cert_one_on");

        Thread.sleep(20000);
        metrics = reloadMetrics();


        // We should see some 503 errors
        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertTrue("There should be some 503 errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertTrue("There should be some 503 errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void testExpiredCert() throws IOException, InterruptedException {

        // Turn mockoauth auth server back on, and make JWT produce SSL errors
        changeAuthServerMode("server", "mode_expired_cert_on");

        Thread.sleep(20000);
        Common.Metrics metrics = reloadMetrics();


        // We should see some TLS errors
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "tls");
        Assert.assertTrue("There should be some TLS errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWT", "outcome", "error", "error_type", "tls");
        Assert.assertTrue("There should be some TLS errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void testNetworkErrors() throws IOException, InterruptedException {

        // Turn off mockoauth auth server to cause network errors
        changeAuthServerMode("server", "mode_off");

        Thread.sleep(20000);
        Common.Metrics metrics = reloadMetrics();


        // See some network errors on JWT's
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "connect");
        Assert.assertTrue("There should be some network errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWT", "outcome", "error", "error_type", "connect");
        Assert.assertTrue("There should be some network errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void postInitCheck() throws IOException {
        Common.Metrics metrics = Common.getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        // mockoauth has JWKS endpoint configured to return 404
        // error counter for 404 for JWT should not be zero as at least one JWKS request should fail
        // during JWT listener's JWTSignatureValidator initialisation
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "404");
        Assert.assertTrue("There should be some 404 errors", new BigDecimal(value).doubleValue() > 0.0);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWT", "outcome", "error", "error_type", "http", "status", "404");
        Assert.assertTrue("There should be some 404 errors", new BigDecimal(value).doubleValue() > 0.0);
    }

    private void zeroCheck() throws IOException {
        Common.Metrics metrics = Common.getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        // assumption check
        // JWT listener config (on port 9404 in docker-compose.yml) has no token endpoint so the next metric should not exist
        String value = metrics.getValue("strimzi_oauth_validation_requests_count", "context", "JWT");
        Assert.assertNull(value);


        // initial checks - all success counters should be null
        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "INTROSPECT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "INTROSPECT", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);

        value = metrics.getValue("strimzi_oauth_http_requests_timetotal", "context", "JWTPLAIN", "outcome", "success");
        Assert.assertNull(value);
    }

    private Common.Metrics reloadMetrics() throws IOException {
        return Common.getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
    }

    private void changeAuthServerMode(String resource, String mode) throws IOException {
        String result = HttpUtil.post(URI.create("http://mockoauth:8091/admin/" + resource + "?mode=" + mode), null, "text/plain", "", String.class);
        Assert.assertEquals("admin server response should be ", mode.toUpperCase(Locale.ROOT), result);
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
        System.out.println();
    }
}
