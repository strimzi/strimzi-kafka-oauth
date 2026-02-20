/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.metrics.TestMetrics;
import io.strimzi.oauth.testsuite.clients.ConcurrentKafkaClientsRunner;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.KafkaPreset;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlain;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceAndConsumePlain;
import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;

@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(preset = KafkaPreset.KEYCLOAK_AUTH))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthOAuthOverPlainIT {

    private static final Logger log = LoggerFactory.getLogger(AuthOAuthOverPlainIT.class);
    private static final String BROKER_KEYCLOAK_HOST = "keycloak:8080";

    OAuthEnvironmentExtension env;

    @Test
    @Order(1)
    @DisplayName("Client credentials over PLAIN with JWT")
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.JWT)
    void clientCredentialsOverPlainWithJwt() throws Exception {
        final String kafkaBootstrap = "localhost:9096";
        final String hostPort = env.getKeycloakHostPort();

        // For metrics
        final String realm = "kafka-authz";
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithJwt";

        produceAndConsumePlain(kafkaBootstrap, plainConfig, topic, "The Message");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwt >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAIN", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwt > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(2)
    @DisplayName("Client credentials over PLAIN with introspection")
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.INTROSPECTION)
    void clientCredentialsOverPlainWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9097";

        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "team-a-client");
        plainConfig.put("password", "team-a-client-secret");

        final String topic = "KeycloakAuthenticationTest-clientCredentialsOverPlainWithIntrospection";

        produceAndConsumePlain(kafkaBootstrap, plainConfig, topic, "The Message");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for introspect >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for introspect > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(3)
    @DisplayName("Access token over PLAIN with introspection")
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.INTROSPECTION)
    void accessTokenOverPlainWithIntrospection() throws Exception {
        final String kafkaBootstrap = "localhost:9097";
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "team-a-client", "team-a-client-secret", true, null, null, true);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "service-account-team-a-client");
        plainConfig.put("password", "$accessToken:" + info.token());

        final String topic = "KeycloakAuthenticationTest-accessTokenOverPlainWithIntrospection";

        produceAndConsumePlain(kafkaBootstrap, plainConfig, topic, "The Message");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "INTROSPECTPLAIN", "kind", "introspect", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 (on top of the previous test)
        Assertions.assertTrue(value != null && value.intValue() >= 8, "strimzi_oauth_validation_requests_count for introspect >= 8");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_http_requests_count for plain >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECTPLAIN", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for plain > 0.0");
    }

    @Test
    @Order(4)
    @DisplayName("Client credentials over PLAIN with flood test")
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.PERFORMANCE)
    void clientCredentialsOverPlainWithFloodTest() throws Exception {
        final String kafkaBootstrap = "localhost:9102";

        String clientPrefix = "kafka-producer-client-";

        ConcurrentKafkaClientsRunner runner = new ConcurrentKafkaClientsRunner();

        // We do 5 iterations - each time hitting the broker with 10 parallel requests
        for (int run = 0; run < 5; run++) {

            for (int i = 1; i <= 10; i++) {
                String clientId = clientPrefix + i;
                String secret = clientId + "-secret";
                String topic = "messages-" + i;

                Map<String, String> plainConfig = new HashMap<>();
                plainConfig.put("username", clientId);
                plainConfig.put("password", secret);
                Properties props = buildProducerConfigPlain(kafkaBootstrap, plainConfig);

                runner.addTask(() -> {
                    try (Producer<String, String> producer = new KafkaProducer<>(props)) {
                        producer.send(new ProducerRecord<>(topic, "Message 0")).get();
                    }
                    return null;
                });
            }

            runner.executeAll(60);
            runner.clear();
        }
    }

    @Test
    @Order(5)
    @DisplayName("Access token over PLAIN with client credentials disabled")
    @Tag(TestTags.PLAIN)
    void accessTokenOverPlainWithClientCredentialsDisabled() throws Exception {
        final String kafkaBootstrap = "localhost:9103";
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        String tokenEndpointPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "team-a-client", "team-a-client-secret", true, null, null, true);

        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", "service-account-team-a-client");
        // we use no prefix ("$accessToken:") because access-token-only mode is in effect
        plainConfig.put("password", info.token());

        final String topic = "KeycloakAuthenticationTest-accessTokenOverPlainWithClientCredentialsDisabled";

        produceAndConsumePlain(kafkaBootstrap, plainConfig, topic, "The Message");

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");

        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2
        Assertions.assertTrue(value != null && value.intValue() >= 4, "strimzi_oauth_validation_requests_count for jwks >= 4");

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "jwks", "mechanism", "PLAIN", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_validation_requests_totaltimems for jwks > 0.0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");

        // There is no inter-broker connection on this listener
        // Validation did not require the broker authenticating in client's name, because the token was passed
        Assertions.assertEquals(0, value.intValue(), "strimzi_oauth_http_requests_count for plain == 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "JWTPLAINWITHOUTCC", "kind", "plain", "host", BROKER_KEYCLOAK_HOST, "path", tokenEndpointPath, "outcome", "success");
        Assertions.assertEquals(0.0, value.doubleValue(), 0.0, "strimzi_oauth_http_requests_totaltimems for plain == 0.0");
    }
}
