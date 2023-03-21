/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth.metrics;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.metrics.GlobalConfig;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.testsuite.oauth.common.TestUtil;
import io.strimzi.testsuite.oauth.mockoauth.Common;
import io.strimzi.testsuite.oauth.mockoauth.LogLineReader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.getPrometheusMetrics;
import static io.strimzi.testsuite.oauth.mockoauth.Common.reloadMetrics;


public class MetricsTest {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsTest.class);

    private final static int PAUSE_MILLIS = 15_000;

    private static final String KAFKA_BOOTSTRAP = "kafka:9092";
    private static final String TOKEN_ENDPOINT_URI = "https://mockoauth:8090/token";


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

        logStart("Check `strimzi.metric.reporters` configuration handling");
        testOAuthMetricReporters();

        logStart("Check enabling metrics with JMX for the Kafka client");
        testKafkaClientConfig();
    }

    private void testKafkaClientConfig() throws Exception {

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "ignored");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "ignored");
        oauthConfig.put(ClientConfig.OAUTH_ENABLE_METRICS, "true");

        Properties producerProps = new Properties();

        LogLineReader logReader = new LogLineReader(Common.LOG_PATH);
        logReader.readNext();

        // Clear the configured metrics in order to trigger reinitialisation
        Services.close();

        try {
            initJaas(oauthConfig, producerProps);
            Assert.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            LOG.debug("[IGNORED] Failed as expected: {}", e.getMessage(), e);
        }

        List<String> lines = logReader.readNext();
        Assert.assertTrue("Contains log warning", TestUtil.checkLogForRegex(lines, "OAuth metrics will not be exported to JMX"));

        producerProps.put(GlobalConfig.STRIMZI_METRIC_REPORTERS, "org.apache.kafka.common.metrics.JmxReporter");

        // Clear the configured metrics in order to trigger reinitialisation
        Services.close();

        try {
            initJaas(oauthConfig, producerProps);
            Assert.fail("Should have failed due to bad access token");

        } catch (Exception e) {
            LOG.debug("[IGNORED] Failed as expected: {}", e.getMessage(), e);
        }

        lines = logReader.readNext();
        Assert.assertFalse("Contains log warning", TestUtil.checkLogForRegex(lines, "OAuth metrics will not be exported to JMX"));
        Assert.assertTrue("Instantiated JMX Reporter", TestUtil.checkLogForRegex(lines, "reporters: \\[org.apache.kafka.common.metrics.JmxReporter"));
    }

    private void initJaas(Map<String, String> oauthConfig, Properties additionalProps) throws Exception {
        Properties producerProps = Common.buildProducerConfigOAuthBearer(KAFKA_BOOTSTRAP, oauthConfig);
        producerProps.putAll(additionalProps);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>("Test-testTopic", "The Message")).get();
        }
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

    private void testOAuthMetricReporters() throws IOException, InterruptedException, TimeoutException {

        LogLineReader logReader = new LogLineReader(Common.LOG_PATH);
        // seek to the end of log file
        logReader.readNext();

        Map<String, String> configs = new HashMap<>();
        configs.put("strimzi.metric.reporters", "io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        reinitServices(configs);
        Services.getInstance().getMetrics();
        LOG.info("Waiting for: reporters: TestMetricsReporter"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("reporters: \\[io\\.strimzi\\.testsuite\\.oauth\\.common\\.metrics\\.TestMetricsReporter");

        configs.remove("strimzi.metric.reporters");
        configs.put("metric.reporters", "io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        reinitServices(configs);
        // No reporter will be instantiated
        Services.getInstance().getMetrics();
        LOG.info("Waiting for reporters warning"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("OAuth metrics will not be exported to JMX");

        LOG.info("Waiting for: reporters: <empty>"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("reporters: \\[\\]");

        configs.put("strimzi.metric.reporters", "org.apache.kafka.common.metrics.JmxReporter");
        reinitServices(configs);
        // Only JmxReporter will be instantiated the other setting is ignored
        Services.getInstance().getMetrics();
        LOG.info("Waiting for: reporters: JmxReporter"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("reporters: \\[org\\.apache\\.kafka\\.common\\.metrics\\.JmxReporter");

        configs.put("strimzi.metric.reporters", "");
        reinitServices(configs);
        // No reporter will be instantiated
        Services.getInstance().getMetrics();
        LOG.info("Waiting for: reporters: <empty>"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("reporters: \\[\\]");

        configs.put("strimzi.metric.reporters", "org.apache.kafka.common.metrics.JmxReporter,io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        reinitServices(configs);
        // JmxReporter and TestMetricsReporter are instantiated
        Services.getInstance().getMetrics();
        LOG.info("Waiting for: reporters: JmxReporter,TestMetricsReporter"); // Make sure to not repeat the below condition in the string here
        logReader.waitFor("reporters: \\[org\\.apache\\.kafka\\.common\\.metrics\\.JmxReporter.*, io\\.strimzi\\.testsuite\\.oauth\\.common\\.metrics\\.TestMetricsReporter");
    }

    private void reinitServices(Map<String, String> configs) {
        Services.close();
        Services.configure(configs);
    }

    private void logStart(String msg) {
        System.out.println("    ====    "  + msg);
    }
}
