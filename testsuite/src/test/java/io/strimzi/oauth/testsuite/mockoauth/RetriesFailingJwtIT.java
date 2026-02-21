/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.utils.TestUtil.waitForCondition;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlain;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;

/**
 * Tests for HTTP retry handling on the FAILINGJWT listener.
 * Validates that broker-side HTTP retries work correctly for PLAIN authentication with JWKS endpoint.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        oauthProperties = {
            "oauth.config.id=FAILINGJWT",
            "oauth.fail.fast=false",
            "oauth.check.access.token.type=false",
            "oauth.token.endpoint.uri=https://mockoauth:8090/failing_token",
            "oauth.jwks.endpoint.uri=https://mockoauth:8090/jwks",
            "oauth.jwks.refresh.seconds=10",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "oauth.http.retries=1",
            "oauth.http.retry.pause.millis=3000",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class RetriesFailingJwtIT {
    // FAILINGJWT listener is configured with 'oauth.http.retry.pause.millis' of 3000
    static final int PAUSE_MILLIS = 3000;

    OAuthEnvironmentExtension env;
    private GenericContainer<?> kafkaContainer;

    @BeforeAll
    void setUp() throws Exception {
        kafkaContainer = env.getKafka();
        // Wait for JWKS keys to be loaded (refresh interval is 10s, wait up to 30s)
        waitForCondition(() ->
            getContainerLogsForString(kafkaContainer, "JWKS keys change detected").size() > 0,
            1000, 30);
    }

    @Test
    @DisplayName("Broker should retry token endpoint on PLAIN with JWKS endpoint configured")
    @Tag(TestTags.RETRY)
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.JWKS)
    void testPlainRetriesWithJWKS() throws Exception {
        // Use FAILINGJWT kafka listener that uses /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = env.getBootstrapServers();

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);
        createOAuthClient("kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);

        // JWKS keys are guaranteed loaded by setUp()'s waitForCondition

        // set failing_token endpoint to always return 500, so that the retry will fail
        changeAuthServerMode("failing_token", "mode_failing_500");


        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            String topic = "RetriesTests-plainRetriesWithJWKSTest";

            // try to produce
            long start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message"))
                    .get();
                Assertions.fail("Should have failed due to failing_token set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            long diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assertions.assertTrue(diff > PAUSE_MILLIS, "It should take at least 3 seconds to fail (" + diff + ")");


            // configure failing_token endpoint so that they only fail every other time
            changeAuthServerMode("failing_token", "mode_400");

            start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message"))
                .get();

            diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assertions.assertTrue(diff > PAUSE_MILLIS, "It should take at least 3 seconds due to token retry (" + diff + ")");
        }
    }
}
