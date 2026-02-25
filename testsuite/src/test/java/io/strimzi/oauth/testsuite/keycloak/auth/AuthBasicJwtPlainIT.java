/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.metrics.TestMetrics;
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
import java.util.Map;

import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceAndConsumeOAuthBearer;
import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;

@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "kafka-authz",
        metrics = true,
        oauthProperties = {
            "oauth.config.id=JWTPLAIN",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class AuthBasicJwtPlainIT {

    private static final Logger log = LoggerFactory.getLogger(AuthBasicJwtPlainIT.class);

    // Keycloak host as seen by the broker (used in Prometheus metrics labels)
    private static final String BROKER_KEYCLOAK_HOST = "keycloak:8080";

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.JWT)
    @Tag(TestTags.RSA)
    void testClientCredentialsWithJwtRSAValidation() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        // For metrics
        String jwksPath = "/realms/" + realm + "/protocol/openid-connect/certs";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtRSAValidationTest";

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, topic, "The Message");

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAIN", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "mechanism", "OAUTHBEARER", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAIN", "kind", "jwks", "host", BROKER_KEYCLOAK_HOST, "path", jwksPath, "mechanism", "OAUTHBEARER", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwks > 0.0");
    }

    @Test
    @Tag(TestTags.JWT)
    @Tag(TestTags.PASSWORD_GRANT)
    void testPasswordGrantWithJwtRSAValidation() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-cli");
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, "alice");
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, "alice-password");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-passwordGrantWithJwtRSAValidationTest";

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, topic, "The Message");
    }
}
