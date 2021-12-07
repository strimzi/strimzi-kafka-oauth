/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.kafka.oauth.client.ClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.authz.Common.buildProducerConfigScram;

public class MultiSaslTest {

    private static final String KAFKA_PLAIN_LISTENER = "kafka:9100";
    private static final String KAFKA_SCRAM_LISTENER = "kafka:9101";
    private static final String KAFKA_JWT_LISTENER = "kafka:9092";
    private static final String KAFKA_JWTPLAIN_LISTENER = "kafka:9094";

    public static void doTest() throws Exception {

        // bobby:bobby-secret
        String username = "bobby";
        String password = "bobby-secret";

        // Producing to PLAIN listener using SASL/PLAIN should succeed.
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        Properties producerProps = producerConfigPlain(KAFKA_PLAIN_LISTENER, username, password);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);

        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain-denied", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'bobby' has not been configured for SCRAM in 'docker/kafka/scripts/start.sh'
        producerProps = producerConfigScram(KAFKA_SCRAM_LISTENER, username, password);
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
        producerProps = producerConfigPlain(KAFKA_PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should succeed.
        // The necessary ACLs have been added by 'docker/kafka-acls/scripts/add-acls.sh'
        producerProps = producerConfigScram(KAFKA_SCRAM_LISTENER, username, password);
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
        producerProps = producerConfigPlain(KAFKA_PLAIN_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-plain", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to SCRAM listener using SASL_SCRAM-SHA-512 should fail.
        // User 'alice' was configured for SASL in 'docker/kafka/scripts/start.sh' but with a different password
        producerProps = producerConfigScram(KAFKA_SCRAM_LISTENER, username, password);
        try {
            produceToTopic("KeycloakAuthorizationTest-multiSaslTest-scram", producerProps);
            Assert.fail("Should have failed");
        } catch (Exception ignored) {
        }

        // Producing to JWT listener using SASL/OAUTHBEARER using access token should succeed
        String accessToken = Common.loginWithUsernamePassword(
                URI.create("http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token"),
                username, password, "kafka-cli");
        producerProps = producerConfigOAuthBearerAccessToken(KAFKA_JWT_LISTENER, accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauthbearer", producerProps);

        // producing to JWTPLAIN listener using SASL/PLAIN using $accessToken should succeed
        producerProps = producerConfigPlain(KAFKA_JWTPLAIN_LISTENER, username, "$accessToken:" + accessToken);
        produceToTopic("KeycloakAuthorizationTest-multiSaslTest-oauth-over-plain", producerProps);
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
