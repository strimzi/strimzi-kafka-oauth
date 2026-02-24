/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.metrics.TestMetrics;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearerWithAccessToken;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlainSimple;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.metrics.TestMetrics.getPrometheusMetrics;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for multiple SASL mechanisms (OAUTHBEARER + OAuth-over-PLAIN) with authorization.
 *
 * Regular PLAIN (username/password without OAuth) is not tested because it conflicts
 * with OAuth-over-PLAIN on the same listener (both use SASL mechanism PLAIN with
 * different callback handlers).
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        authenticationType = AuthenticationType.NONE,
        setupAcls = true,
        metrics = true,
        kafkaProperties = {
            "sasl.enabled.mechanisms=OAUTHBEARER,PLAIN",
            "listener.security.protocol.map=PLAINTEXT:SASL_PLAINTEXT,BROKER1:PLAINTEXT,CONTROLLER:PLAINTEXT",
            "listener.name.plaintext.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + " oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\""
                + " oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\""
                + " oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\""
                + " oauth.client.id=\"kafka\""
                + " oauth.client.secret=\"kafka-secret\""
                + " oauth.groups.claim=\"$.realm_access.roles\""
                + " oauth.fallback.username.claim=\"username\""
                + " unsecuredLoginStringClaim_sub=\"admin\" ;",
            "listener.name.plaintext.oauthbearer.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler",
            "listener.name.plaintext.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required"
                + " oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\""
                + " oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\""
                + " oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\" ;",
            "listener.name.plaintext.plain.sasl.server.callback.handler.class=io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler",
            "principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder",
            "offsets.topic.replication.factor=1",
            "authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer",
            "strimzi.authorization.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
            "strimzi.authorization.client.id=kafka",
            "strimzi.authorization.client.secret=kafka-secret",
            "strimzi.authorization.kafka.cluster.name=my-cluster",
            "strimzi.authorization.delegate.to.kafka.acl=true",
            "strimzi.authorization.read.timeout.seconds=45",
            "strimzi.authorization.grants.refresh.pool.size=4",
            "strimzi.authorization.grants.refresh.period.seconds=10",
            "strimzi.authorization.http.retries=1",
            "strimzi.authorization.reuse.grants=true",
            "strimzi.authorization.enable.metrics=true",
            "super.users=User:admin;User:service-account-kafka;User:ANONYMOUS"
        }
    )
)
public class AuthzMultiSaslIT {

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.MULTI_SASL)
    @Tag(TestTags.AUTHORIZATION)
    public void testMultipleSaslMechanisms() throws Exception {
        String kafkaBootstrap = env.getBootstrapServers();

        // for metrics
        String authHostPort = "keycloak:8080";
        String realm = "kafka-authz";
        String tokenPath =  "/realms/" + realm + "/protocol/openid-connect/token";

        // alice:alice-password
        String username = "alice";
        String password = "alice-password";

        int fetchGrantsCount = currentFetchGrantsLogCount();

        // Producing to listener using SASL/OAUTHBEARER using access token should succeed
        String accessToken = KafkaClientsConfig.loginWithUsernamePasswordInBody(
                URI.create(env.getTokenEndpointUri()),
                username, password, "kafka-cli");
        Properties producerProps = buildProducerConfigOAuthBearerWithAccessToken(kafkaBootstrap, accessToken);
        produceMessage(producerProps, "KeycloakAuthorizationTest-multiSaslTest-oauthbearer", "The Message");

        checkGrantsFetchCountDiff(fetchGrantsCount);

        // Producing using SASL/PLAIN with $accessToken should succeed (OAuth-over-PLAIN)
        producerProps = buildProducerConfigPlainSimple(kafkaBootstrap, username, "$accessToken:" + accessToken);
        produceMessage(producerProps, "KeycloakAuthorizationTest-multiSaslTest-oauth-over-plain", "The Message");

        // Test the grants reuse feature
        checkGrantsFetchCountDiff(fetchGrantsCount);

        // check metrics
        checkAuthorizationRequestsMetrics(authHostPort, tokenPath);
        checkGrantsMetrics(authHostPort, tokenPath);
    }

    private int currentFetchGrantsLogCount() {
        List<String> lines = getContainerLogsForString(env.getKafka(), "Fetching grants from Keycloak");
        return lines.size();
    }

    private void checkGrantsFetchCountDiff(int previousFetchGrantsCount) {
        int current = currentFetchGrantsLogCount();
        assertEquals(1, current - previousFetchGrantsCount, "Expected one grants fetch");
    }

    private void checkGrantsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.intValue() > 0, "strimzi_oauth_http_requests_count for keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_http_requests_totaltimems for keycloak-authorization > 0");
    }

    private void checkAuthorizationRequestsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create(env.getMetricsUri()));

        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.intValue() > 0, "strimzi_oauth_authorization_requests_count for successful keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        assertTrue(value.doubleValue() > 0.0, "strimzi_oauth_authorization_requests_totaltimems for successful keycloak-authorization > 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_count", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        assertEquals(0, value.intValue(), "strimzi_oauth_authorization_requests_count for failed keycloak-authorization == 0");

        value = metrics.getStartsWithValueSum("strimzi_oauth_authorization_requests_totaltimems", "kind", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        assertEquals(0.0, value.doubleValue(), "strimzi_oauth_authorization_requests_totaltimems for failed keycloak-authorization == 0");
    }

}
