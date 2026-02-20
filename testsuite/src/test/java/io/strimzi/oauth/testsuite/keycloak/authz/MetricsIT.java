/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.metrics.TestMetrics;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.KafkaPreset;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceAndConsumeOAuthBearer;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for OAuth metrics functionality
 */
@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(preset = KafkaPreset.KEYCLOAK_AUTHZ, setupAcls = true, metrics = true))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetricsIT {

    private static final String AUTH_HOST_PORT = "keycloak:8080";
    private static final String REALM = "kafka-authz";
    private static final String JWKS_PATH = "/realms/" + REALM + "/protocol/openid-connect/certs";
    private static final String TOKEN_PATH = "/realms/" + REALM + "/protocol/openid-connect/token";

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Verify JWKS and client authentication metrics")
    @Tag(TestTags.METRICS)
    public void verifyJwksAndClientAuthMetrics() throws Exception {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", AUTH_HOST_PORT, "path", JWKS_PATH, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_count for jwks > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", AUTH_HOST_PORT, "path", JWKS_PATH, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for jwks > 0.0");

        // There should be at least 2 client authentication requests - those for inter-broker connection on JWT listener
        value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_count", "kind", "client-auth", "outcome", "success");
        assertTrue(value.intValue() >= 2, "strimzi_oauth_authentication_requests_count for client-auth >= 2");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_totaltimems", "kind", "client-auth", "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_authentication_requests_totaltimems for client-auth > 0.0");
    }

    @Test
    @DisplayName("Verify validation and authorization metrics")
    @Tag(TestTags.METRICS)
    public void verifyValidationAndAuthorizationMetrics() throws Exception {
        // In KRaft single-node mode, there are no inter-broker connections on the JWT listener,
        // so we need to produce a message to trigger OAUTHBEARER token validation and authorization.
        String kafkaBootstrap = "localhost:9092";
        String hostPort = env.getKeycloakHostPort();
        String tokenEndpointUri = "http://" + hostPort + "/realms/" + REALM + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, "a_MetricsIT", "The Message");

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        assertTrue(value.intValue() > 0, "strimzi_oauth_validation_requests_count for jwks > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwks > 0.0");

        // No 403 (no grants) responses in this test
        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", AUTH_HOST_PORT, "path", TOKEN_PATH, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_count for keycloak-authorization > 0.0");
    }
}
