/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.testsuite.oauth.authz.kraft.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.authz.Common.produceToTopic;
import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;
import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for multiple SASL mechanisms (PLAIN, OAUTHBEARER, OAuth over PLAIN)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MultiSaslIT {

    private static final String PLAIN_LISTENER = "localhost:9100";
    private static final String JWT_LISTENER = "localhost:9092";
    private static final String JWTPLAIN_LISTENER = "localhost:9094";

    private KeycloakAuthzKRaftTestEnvironment environment;
    private GenericContainer<?> kafkaContainer;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @DisplayName("Test multiple SASL mechanisms with authorization")
    @Tag("multi-sasl")
    @Tag("authorization")
    public void testMultipleSaslMechanisms() throws Exception {

        // bobby:bobby-secret is defined in docker-compose.yaml in the PLAIN listener configuration (port 9100)
        String username = "bobby";
        String password = "bobby-secret";

        // for metrics
        String authHostPort = "keycloak:8080";
        String realm = "kafka-authz";
        String tokenPath =  "/realms/" + realm + "/protocol/openid-connect/token";

        // Producing to PLAIN listener using SASL/PLAIN should succeed.
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        Properties producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);

        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain-denied", producerProps);
            fail("Should have failed");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthorizationException.class, e.getCause(), "Instance of authorization exception");
        }

        // alice:alice-password
        username = "alice";
        password = "alice-password";

        // Producing to PLAIN listener using SASL/PLAIN should fail.
        // User 'alice' was not configured in PLAIN listener jaas configuration (port 9100) in 'docker-compose.yml'
        producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            fail("Should have failed");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthenticationException.class, e.getCause(), "Instance of authentication exception");
        }

        int fetchGrantsCount = currentFetchGrantsLogCount();

        // Producing to JWT listener using SASL/OAUTHBEARER using access token should succeed
        String accessToken = Common.loginWithUsernamePassword(
                URI.create(Common.tokenEndpointUri()),
                username, password, "kafka-cli");
        producerProps = producerConfigOAuthBearerAccessToken(JWT_LISTENER, accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauthbearer", producerProps);

        checkGrantsFetchCountDiff(fetchGrantsCount);

        // producing to JWTPLAIN listener using SASL/PLAIN using $accessToken should succeed
        producerProps = producerConfigPlain(JWTPLAIN_LISTENER, username, "$accessToken:" + accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauth-over-plain", producerProps);

        // Test the grants reuse feature
        checkGrantsFetchCountDiff(fetchGrantsCount);

        // check metrics
        checkAuthorizationRequestsMetrics(authHostPort, tokenPath);
        checkGrantsMetrics(authHostPort, tokenPath);
    }

    private int currentFetchGrantsLogCount() {
        List<String> lines = getContainerLogsForString(kafkaContainer, "Fetching grants from Keycloak");
        return lines.size();
    }

    private void checkGrantsFetchCountDiff(int previousFetchGrantsCount) {
        int current = currentFetchGrantsLogCount();
        assertEquals(1, current - previousFetchGrantsCount, "Expected one grants fetch");
    }

    private static void checkGrantsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.intValue() > 0, "strimzi_oauth_http_requests_count for keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for keycloak-authorization > 0");

        // There are 403 responses in Zookeeper mode, but not in KRaft mode
        // Apparently the inter-broker session to JWT listener is not attempted in KRaft mode

        //value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        //assertTrue(value.intValue() > 0, "strimzi_oauth_http_requests_count with no-grants for keycloak-authorization > 0");

        //value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        //assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems with no-grants for keycloak-authorization > 0");
    }

    private static void checkAuthorizationRequestsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://localhost:9404/metrics"));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.intValue() > 0, "strimzi_oauth_authorization_requests_count for successful keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_authorization_requests_totaltimems for successful keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        assertEquals(0, value.intValue(), "strimzi_oauth_authorization_requests_count for failed keycloak-authorization == 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        assertEquals(0.0, value.doubleValue(), "strimzi_oauth_authorization_requests_totaltimems for failed keycloak-authorization == 0");
    }

    private static Properties producerConfigPlain(String kafkaBootstrap, String username, String password) {
        Map<String, String> scramConfig = new HashMap<>();
        scramConfig.put("username", username);
        scramConfig.put("password", password);

        return buildProducerConfigPlain(kafkaBootstrap, scramConfig);
    }

    private static Properties producerConfigOAuthBearerAccessToken(String kafkaBootstrap, String accessToken) {
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, accessToken);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        return buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
    }
}
