/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.createOAuthClient;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getRootCause;

/**
 * Tests for HTTP retry handling on the INTROSPECTTIMEOUT listener.
 * Validates that client-side HTTP retries work correctly for token endpoint requests.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        oauthProperties = {
            "oauth.config.id=INTROSPECTTIMEOUT",
            "oauth.connect.timeout.seconds=5",
            "oauth.introspection.endpoint.uri=https://mockoauth:8090/introspect",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class RetriesIntrospectTimeoutIT {
    // FAILINGINTROSPECT and FAILINGJWT listeners are configured with 'oauth.http.retry.pause.millis' of 3000
    static final int PAUSE_MILLIS = 3000;

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Client should retry token endpoint requests according to http.retries config")
    @Tag(TestTags.RETRY)
    @Tag(TestTags.CLIENT)
    void testClientRetries() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = MockOAuthAdmin.getMockOAuthAuthHostPort();

        final String tokenEndpointUri = "https://" + hostPort + "/failing_token";

        // token endpoint set to failing
        changeAuthServerMode("failing_token", "MODE_400");

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        // configure producer with no http.retries
        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, testClient);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, testSecret);
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        try {
            new KafkaProducer<>(producerProps);

            Assertions.fail("Should fail with KafkaException");
        } catch (Exception e) {
            // token endpoint is tried and fails
            // get the exception

            Assertions.assertTrue(e instanceof KafkaException, "is instanceof KafkaException");
            Assertions.assertTrue(getRootCause(e).toString()
                .contains("LoginException"), "Failed due to LoginException");
        }

        // repeat, it should succeed
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            // producer created successfully
        }

        // now create a new producer with http.retries = 1 and some pause
        oauthConfig.put(Config.OAUTH_HTTP_RETRIES, "1");
        oauthConfig.put(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, String.valueOf(PAUSE_MILLIS));

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        long start = System.currentTimeMillis();
        try (KafkaProducer<String, String> p = new KafkaProducer<>(producerProps)) {
            // token endpoint is tried and fails
            // should automatically retry and succeed
            Assertions.assertTrue(System.currentTimeMillis() - start > PAUSE_MILLIS, "It should take at least 3 seconds to get a token");
        }
    }
}
