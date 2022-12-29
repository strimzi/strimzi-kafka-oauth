/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.junit.Assert;

import java.math.BigDecimal;
import java.net.URI;

import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;

public class MetricsTest {

    public static void doTest() throws Exception {

        final String authHostPort = "keycloak:8080";
        final String realm = "kafka-authz";
        final String jwksPath = "/auth/realms/" + realm + "/protocol/openid-connect/certs";
        final String tokenPath = "/auth/realms/" + realm + "/protocol/openid-connect/token";

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", authHostPort, "path", jwksPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for jwks > 0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", authHostPort, "path", jwksPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        // Accross all the listeners there should only be 2 client authentication requests - those for inter-broker connection on JWT listener
        value = metrics.getValueSum("strimzi_oauth_authentication_requests_count", "kind", "client-auth", "outcome", "success");
        Assert.assertEquals("strimzi_oauth_authentication_requests_count for client-auth == 2", 2, value.intValue());

        value = metrics.getValueSum("strimzi_oauth_authentication_requests_totaltimems", "kind", "client-auth", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authentication_requests_totaltimems for client-auth > 0.0", value.doubleValue() > 0.0);

        // Inter-broker auth triggered the only successful validation request
        value = metrics.getValueSum("strimzi_oauth_validation_requests_count", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assert.assertEquals("strimzi_oauth_validation_requests_count for jwks == 1", 1, value.intValue());

        value = metrics.getValueSum("strimzi_oauth_validation_requests_totaltimems", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_validation_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        Assert.assertTrue("strimzi_oauth_http_requests_count for keycloak-authorization > 0.0", value.doubleValue() > 0.0);
    }
}
