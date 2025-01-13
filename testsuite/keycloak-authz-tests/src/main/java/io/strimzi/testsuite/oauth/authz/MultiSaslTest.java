/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.Assert;

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

@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public class MultiSaslTest {

    private static final String PLAIN_LISTENER = "kafka:9100";

    private static final String JWT_LISTENER = "kafka:9092";
    private static final String JWTPLAIN_LISTENER = "kafka:9094";

    private final String kafkaContainer;

    public MultiSaslTest(String kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    public void doTest() throws Exception {

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
            Assert.fail("Should have failed");
        } catch (ExecutionException e) {
            Assert.assertTrue("Instance of authorization exception", e.getCause() instanceof AuthorizationException);
        }

        // alice:alice-password
        username = "alice";
        password = "alice-password";

        // Producing to PLAIN listener using SASL/PLAIN should fail.
        // User 'alice' was not configured in PLAIN listener jaas configuration (port 9100) in 'docker-compose.yml'
        producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            Assert.fail("Should have failed");
        } catch (ExecutionException e) {
            Assert.assertTrue("Instance of authentication exception", e.getCause() instanceof AuthenticationException);
        }

        int fetchGrantsCount = currentFetchGrantsLogCount();

        // Producing to JWT listener using SASL/OAUTHBEARER using access token should succeed
        String accessToken = Common.loginWithUsernamePassword(
                URI.create("http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token"),
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
        Assert.assertEquals("Expected one grants fetch", 1, current - previousFetchGrantsCount);
    }

    private static void checkGrantsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for keycloak-authorization > 0", value.intValue() > 0);

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for keycloak-authorization > 0", value.doubleValue() > 0.0);

        // There are 403 responses in Zookeeper mode, but not in KRaft mode
        // Apparently the inter-broker session to JWT listener is not attempted in KRaft mode

        //value = metrics.getValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        //Assert.assertTrue("strimzi_oauth_http_requests_count with no-grants for keycloak-authorization > 0", value.intValue() > 0);

        //value = metrics.getValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        //Assert.assertTrue("strimzi_oauth_http_requests_totaltimems with no-grants for keycloak-authorization > 0", value.doubleValue() > 0.0);
    }

    private static void checkAuthorizationRequestsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authorization_requests_count for successful keycloak-authorization > 0", value.intValue() > 0);

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authorization_requests_totaltimems for successful keycloak-authorization > 0", value.doubleValue() > 0.0);

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        Assert.assertEquals("strimzi_oauth_authorization_requests_count for failed keycloak-authorization == 0", 0, value.intValue());

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        Assert.assertEquals("strimzi_oauth_authorization_requests_totaltimems for failed keycloak-authorization == 0", 0.0, value.doubleValue(), 0.0);
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
