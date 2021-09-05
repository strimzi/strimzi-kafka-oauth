/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigScram;
import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;

public class MultiSaslTest {

    private static final String PLAIN_LISTENER = "kafka:9100";
    private static final String SCRAM_LISTENER = "kafka:9101";
    private static final String JWT_LISTENER = "kafka:9092";
    private static final String JWTPLAIN_LISTENER = "kafka:9094";

    public static void doTest() throws Exception {

        // bobby:bobby-secret
        String username = "bobby";
        String password = "bobby-secret";

        // for metrics
        String authHostPort = "keycloak:8080";
        String realm = "kafka-authz";
        String tokenPath =  "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // Producing to PLAIN listener using SASL/PLAIN should succeed.
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        Properties producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);

        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain-denied", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'bobby' has not been configured for SCRAM in 'docker/kafka/scripts/start.sh'
        producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }


        // alice:alice-secret
        username = "alice";
        password = "alice-secret";

        // Producing to PLAIN listener using SASL/PLAIN should fail.
        // User 'alice' has not been configured for PLAIN in PLAIN listener configuration in 'docker-compose.yml'
        producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should succeed.
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram-denied", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // OAuth authentication should fail
        try {
            Common.loginWithUsernamePassword(
                    URI.create("http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token"),
                    username, password, "kafka-cli");

            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }


        // alice:alice-password
        username = "alice";
        password = "alice-password";

        // Producing to PLAIN listener using SASL/PLAIN should fail.
        // User 'alice' was not configured for PLAIN in 'docker-compose.yml'
        producerProps = producerConfigPlain(PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'alice' was configured for SASL in 'docker/kafka/scripts/start.sh' but with a different password
        producerProps = producerConfigScram(SCRAM_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to JWT listener using SASL/OAUTHBEARER using access token should succeed
        String accessToken = Common.loginWithUsernamePassword(
                URI.create("http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token"),
                username, password, "kafka-cli");
        producerProps = producerConfigOAuthBearerAccessToken(JWT_LISTENER, accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauthbearer", producerProps);

        // producing to JWTPLAIN listener using SASL/PLAIN using $accessToken should succeed
        producerProps = producerConfigPlain(JWTPLAIN_LISTENER, username, "$accessToken:" + accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauth-over-plain", producerProps);

        // check metrics
        checkAuthorizationRequestsMetrics(authHostPort, tokenPath);
        checkGrantsMetrics(authHostPort, tokenPath);
    }

    private static void checkGrantsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getValueSum("strimzi_oauth_http_requests_count", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for keycloak-authorization > 0", value.intValue() > 0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_timetotal", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_timetotal for keycloak-authorization > 0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_count", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        Assert.assertTrue("strimzi_oauth_http_requests_count with no-grants for keycloak-authorization > 0", value.intValue() > 0);

        value = metrics.getValueSum("strimzi_oauth_http_requests_timetotal", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error", "status", "403");
        Assert.assertTrue("strimzi_oauth_http_requests_timetotal with no-grants for keycloak-authorization > 0", value.doubleValue() > 0.0);
    }

    private static void checkAuthorizationRequestsMetrics(String authHostPort, String tokenPath) throws IOException {
        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));

        BigDecimal value = metrics.getValueSum("strimzi_oauth_authorization_requests_count", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authorization_requests_count for successful keycloak-authorization > 0", value.intValue() > 0);

        value = metrics.getValueSum("strimzi_oauth_authorization_requests_timetotal", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_authorization_requests_timetotal for successful keycloak-authorization > 0", value.doubleValue() > 0.0);

        value = metrics.getValueSum("strimzi_oauth_authorization_requests_count", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        Assert.assertEquals("strimzi_oauth_authorization_requests_count for failed keycloak-authorization == 0", 0, value.intValue());

        value = metrics.getValueSum("strimzi_oauth_authorization_requests_timetotal", "type", "keycloak-authorization", "host", authHostPort, "path", tokenPath, "outcome", "error");
        Assert.assertEquals("strimzi_oauth_authorization_requests_timetotal for failed keycloak-authorization == 0", 0.0, value.doubleValue(), 0.0);
    }

    private static Properties producerConfigScram(String kafkaBootstrap, String username, String password) {
        Map<String, String> scramConfig = new HashMap<>();
        scramConfig.put("username", username);
        scramConfig.put("password", password);

        return buildProducerConfigScram(kafkaBootstrap, scramConfig);
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

    private static void produceToTopic(String topic, Properties config) throws Exception {

        Producer<String, String> producer = new KafkaProducer<>(config);

        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");
    }
}
