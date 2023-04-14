/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.junit.Assert;

import java.math.BigDecimal;
import java.net.URI;

import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;

@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public class MetricsTest {

    private static final String AUTH_HOST_PORT = "keycloak:8080";
    private static final String REALM = "kafka-authz";
    private static final String JWKS_PATH = "/auth/realms/" + REALM + "/protocol/openid-connect/certs";

    public static void doTest() throws Exception {

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", AUTH_HOST_PORT, "path", JWKS_PATH, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for jwks > 0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", AUTH_HOST_PORT, "path", JWKS_PATH, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        // Accross all the listeners there should only be 2 client authentication requests - those for inter-broker connection on JWT listener
        value = metrics.getValueSum("strimzi_oauth_authentication_requests_count", "kind", "client-auth", "outcome", "success");
        Assert.assertEquals("strimzi_oauth_authentication_requests_count for client-auth == 2", 2, value.intValue());

        value = metrics.getValueSum("strimzi_oauth_authentication_requests_totaltimems", "kind", "client-auth", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authentication_requests_totaltimems for client-auth > 0.0", value.doubleValue() > 0.0);
    }

    public static void doTestValidationAndAuthorization() throws Exception {

        final String tokenPath = "/auth/realms/" + REALM + "/protocol/openid-connect/token";

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        BigDecimal value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_count for jwks > 0", value.intValue() > 0);

        value = metrics.getValueSum("strimzi_oauth_validation_requests_totaltimems", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        // No 403 (no grants) responses in this test
        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", AUTH_HOST_PORT, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for keycloak-authorization > 0.0", value.doubleValue() > 0.0);
    }
}
