/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.metrics;

import org.junit.Assert;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getPrometheusMetrics;
import static io.strimzi.testsuite.oauth.mockoauth.Common.reloadMetrics;


public class MetricsTest {

    private final static int PAUSE_MILLIS = 15_000;

    public void doTest() throws Exception {

        logStart("Check there are no Prometheus metrics errors before the tests are run");
        zeroCheck();

        logStart("Check initial JWKS endpoint errors show in Prometheus metrics");
        postInitCheck();

        logStart("Check network errors show in Prometheus metrics");
        testNetworkErrors();

        logStart("Check TLS errors show in Prometheus metrics");
        testExpiredCert();

        logStart("Check 5xx errors show in Prometheus metrics");
        testInternalServerErrors();
    }

    private void testInternalServerErrors() throws IOException, InterruptedException {

        // Configure jwks endpoint to return 503
        changeAuthServerMode("jwks", "mode_503");
        // Turn mockoauth auth server back on with cert no. 2
        changeAuthServerMode("server", "mode_cert_two_on");

        Thread.sleep(PAUSE_MILLIS);
        Metrics metrics = reloadMetrics();


        // We should not see any 503 errors yet because of more TLS errors due to CERT_TWO being used
        String value = metrics.getValue("strimzi_oauth_http_requests_count", "context", "JWT", "outcome", "error", "error_type", "http", "status", "503");
        Assert.assertNull("There should be no 503 errors", value);
        // Switch mockoauth auth server back to using cert no. 1
        changeAuthServerMode("server", "mode_cert_one_on");

        Thread.sleep(PAUSE_MILLIS);
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

        Thread.sleep(PAUSE_MILLIS);
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

        Thread.sleep(PAUSE_MILLIS * 2);
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

    private void logStart(String msg) {
        System.out.println("    ====    "  + msg);
    }
}
