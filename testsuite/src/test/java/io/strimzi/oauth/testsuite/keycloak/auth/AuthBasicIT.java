/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.metrics.TestMetrics;
import io.strimzi.oauth.testsuite.metrics.TestMetricsReporter;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceAndConsumeOAuthBearer;
import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;

@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "demo-ec",
        metrics = true,
        oauthProperties = {
            "oauth.config.id=JWT",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        },
        kafkaProperties = {
            "metrics.num.samples=3",
            "metrics.recording.level=DEBUG",
            "metrics.sample.window.ms=15000",
            "metrics.context.test.label=testvalue"
        }
    )
)
public class AuthBasicIT {

    private static final Logger log = LoggerFactory.getLogger(AuthBasicIT.class);

    // Keycloak host as seen by the broker (used in Prometheus metrics labels)
    private static final String BROKER_KEYCLOAK_HOST = "keycloak:8080";

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.METRICS)
    void testOauthMetricsConfigIntegration() {
        // Test that MetricReporter config works as expected
        // Get kafka log and make sure the TestMetricReporter was initialised exactly twice
        List<String> lines = getContainerLogsForString(env.getKafka(), "TestMetricsReporter no. ");
        Assertions.assertEquals(1, lines.size(), "Kafka log should contain: \"TestMetricsReporter no. \" exactly once");
        Assertions.assertTrue(lines.get(0).contains("TestMetricsReporter no. 1 "), "Contains \"TestMetricsReporter no. 1\"");

        // Ensure the configuration was applied as expected
        lines = getContainerLogsForString(env.getKafka(), "Creating Metrics:");
        String line = lines.get(1);
        Assertions.assertTrue(line.contains("samples: 3"), "samples: 3");
        Assertions.assertTrue(line.contains("recordingLevel: DEBUG"), "recordingLevel: DEBUG");
        Assertions.assertTrue(line.contains("timeWindowMs: 15000"), "timeWindowMs: 15000");

        line = lines.get(2);
        Assertions.assertTrue(line.contains("test.label=testvalue"), "test.label=testvalue");
        Assertions.assertTrue(line.contains("_namespace=strimzi.oauth"), "_namespace=strimzi.oauth");
        Assertions.assertTrue(line.contains("kafka.node.id=0"), "kafka.node.id=0");

        line = lines.get(3);
        Assertions.assertTrue(line.contains(TestMetricsReporter.class.getName()), TestMetricsReporter.class.getName());
        Assertions.assertTrue(line.contains("org.apache.kafka.common.metrics.JmxReporter"), "org.apache.kafka.common.metrics.JmxReporter");
    }

    @Test
    @Tag(TestTags.JWT)
    @Tag(TestTags.ECDSA)
    void testClientCredentialsWithJwtECDSAValidation() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "demo-ec";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        // For metrics
        final String jwksPath = "/realms/" + realm + "/protocol/openid-connect/certs";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtECDSAValidationTest";

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, topic, "The Message");

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_count for jwks > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for jwks > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWT", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 validations
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWT", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for jwks > 0.0");
    }
}
