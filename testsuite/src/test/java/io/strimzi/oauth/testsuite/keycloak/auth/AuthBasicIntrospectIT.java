/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TokenInfo;
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
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.TokenIntrospection.introspectAccessToken;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildConsumerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernameForRefreshToken;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernamePassword;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.consumeAndAssert;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceAndConsumeOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.waitForCondition;
import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;

@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "demo",
        clientId = "kafka-broker",
        clientSecret = "kafka-broker-secret",
        metrics = true,
        oauthProperties = {
            "oauth.config.id=INTROSPECT",
            "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/demo/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.groups.claim=$.groups",
            "oauth.token.endpoint.uri=http://keycloak:8080/realms/demo/protocol/openid-connect/token",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        },
        kafkaProperties = {
            // Use the strimzi OAuth login handler so the broker does a real OAuth client-credentials
            // login at startup. Without this, the default unsecured handler is used (no HTTP call,
            // no client-auth metrics).
            "listener.name.plaintext.oauthbearer.sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler"
        }
    )
)
public class AuthBasicIntrospectIT {

    private static final Logger log = LoggerFactory.getLogger(AuthBasicIntrospectIT.class);

    // Keycloak host as seen by the broker (used in Prometheus metrics labels)
    private static final String BROKER_KEYCLOAK_HOST = "keycloak:8080";

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.METRICS)
    void oauthMetricsClientAuth() throws Exception {
        final String realm = "demo";
        final String tokenPath = "/realms/" + realm + "/protocol/openid-connect/token";

        // The broker's login callback handler (JaasClientOauthLoginCallbackHandler) does a
        // real OAuth client-credentials login at startup, which increments client-auth metrics.
        // Wait for the metric to appear (login may complete slightly after the broker is ready).
        waitForCondition(() -> {
            try {
                TestMetrics m = getPrometheusMetrics(URI.create(env.getMetricsUri()));
                BigDecimal v = m.getStartsWithValueSum("strimzi_oauth_authentication_requests_count",
                    "context", "INTROSPECT", "kind", "client-auth", "outcome", "success");
                return v.intValue() >= 1;
            } catch (Exception e) {
                return false;
            }
        }, 1000, 30);

        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));

        // Request for token from login callback handler
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_count", "context", "INTROSPECT", "kind", "client-auth", "outcome", "success");
        Assertions.assertTrue(value.intValue() >= 1, "strimzi_oauth_authentication_requests_count for client-auth >= 1");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authentication_requests_totaltimems", "context", "INTROSPECT", "kind", "client-auth", "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_authentication_requests_totaltimems for client-auth > 0.0");

        // Authentication to keycloak to exchange clientId + secret for an access token during login callback handler call
        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "context", "INTROSPECT", "kind", "client-auth", "host", BROKER_KEYCLOAK_HOST, "path", tokenPath, "outcome", "success");
        Assertions.assertTrue(value.intValue() >= 1, "strimzi_oauth_http_requests_count for client-auth >= 1");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "context", "INTROSPECT", "kind", "client-auth", "host", BROKER_KEYCLOAK_HOST, "path", tokenPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for client-auth > 0.0");
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    void accessTokenWithIntrospection() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        final String introspectPath = "/realms/" + realm + "/protocol/openid-connect/token/introspect";

        final String tokenEndpointUri = "http://" + authHostPort + path;
        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // First, request access token using client id and secret
        TokenInfo info = loginWithClientSecret(URI.create(tokenEndpointUri), null, null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-accessTokenWithIntrospectionTest";

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, topic, "The Message");

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        // Inter-broker connection did some validation, producer and consumer did some
        Assertions.assertTrue(value != null && value.intValue() >= 5, "strimzi_oauth_http_requests_count for introspect >= 5");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for introspect > 0.0");
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    void refreshTokenWithIntrospection() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        // For metrics
        final String introspectPath = "/realms/" + realm + "/protocol/openid-connect/token/introspect";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        final String clientId = "kafka-cli";
        final String username = "alice";
        final String password = "alice-password";

        // First, request access token using client id and secret
        String refreshToken = loginWithUsernameForRefreshToken(URI.create(tokenEndpointUri), username, password, clientId);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, clientId);
        oauthConfig.put(ClientConfig.OAUTH_REFRESH_TOKEN, refreshToken);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");
        oauthConfig.put(ClientConfig.OAUTH_SCOPE, "profile");

        final String topic = "KeycloakAuthenticationTest-refreshTokenWithIntrospectionTest";

        produceAndConsumeOAuthBearer(kafkaBootstrap, oauthConfig, topic, "The Message");

        // Check metrics
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        // On top of the access token test, producer and consumer together did 4 requests
        Assertions.assertTrue(value != null && value.intValue() >= 9, "strimzi_oauth_http_requests_count for introspect >= 9");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "introspect", "host", BROKER_KEYCLOAK_HOST, "path", introspectPath, "outcome", "success");
        Assertions.assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for introspect > 0.0");
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.PASSWORD_GRANT)
    void passwordGrantWithIntrospection() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String authHostPort = env.getKeycloakHostPort();
        final String realm = "demo";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        final String username = "alice";
        final String password = "alice-password";
        final String clientId = "kafka";

        // Request access token using username and password with public client
        String accessToken = loginWithUsernamePassword(URI.create(tokenEndpointUri), username, password, clientId);

        TokenInfo tokenInfo = introspectAccessToken(accessToken,
            new PrincipalExtractor("preferred_username"));

        Assertions.assertEquals(username, tokenInfo.principal(), "Token contains 'preferred_username' claim with value equal to username");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, clientId);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_USERNAME, username);
        oauthConfig.put(ClientConfig.OAUTH_PASSWORD_GRANT_PASSWORD, password);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-passwordGrantWithIntrospectionTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, "The Message");

        // Authenticate using the username and password and a confidential client - different clientId + secret for consumer
        String confidentialClientId = "kafka-producer-client";
        String confidentialClientSecret = "kafka-producer-client-secret";

        accessToken = loginWithUsernamePassword(URI.create(tokenEndpointUri), username, password, confidentialClientId, confidentialClientSecret);

        tokenInfo = introspectAccessToken(accessToken,
            new PrincipalExtractor("preferred_username"));

        Assertions.assertEquals(username, tokenInfo.principal(), "Token contains 'preferred_username' claim with value equal to username");

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, confidentialClientId);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, confidentialClientSecret);

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        consumeAndAssert(consumerProps, topic, "The Message");
    }
}
