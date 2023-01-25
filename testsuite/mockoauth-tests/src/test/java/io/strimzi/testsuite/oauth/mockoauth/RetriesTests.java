/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.testsuite.oauth.common.TestUtil.getContainerLogsForString;
import static io.strimzi.testsuite.oauth.mockoauth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.mockoauth.Common.buildProducerConfigPlain;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.loginWithClientSecret;

public class RetriesTests {

    // FAILINGINTROSPECT and FAILINGJWT listeners are configured with 'oauth.http.retry.pause.millis' of 3000
    static final int PAUSE_MILLIS = 3000;

    // FAILINGJWT listener is configured with 'oauth.jwks.refresh.seconds' of 10
    static final int JWKS_REFRESH_PERIOD_MILLIS = 10_000;


    private final String kafkaContainer;

    public RetriesTests(String kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    public void doTests() throws Exception {
        // set /jwks endpoint to operate properly
        // used in testPlainRetriesWithJWKS, the listener needs some time to repeat the keys refresh
        // which can happen in the background while we run other tests
        changeAuthServerMode("jwks", "mode_200");

        testClientRetries();
        testIntrospectAndUserinfoEndpointsRetries();
        testPlainIntrospectAndUserinfoEndpointsRetries();
        testPlainRetriesWithJWKS();
    }

    private void testPlainIntrospectAndUserinfoEndpointsRetries() throws Exception {
        System.out.println("    ====    Check that broker connecting to token, introspection, and userinfo endpoints on PLAIN uses http.retries config");

        // Use FAILINGINTROSPECT kafka listener that uses /failing_introspect, /failing_userinfo,
        // and /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = "kafka:9097";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);
        createOAuthClient("kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            // configure the endpoints so that they are in failing mode
            changeAuthServerMode("failing_token", "mode_400");
            changeAuthServerMode("failing_introspect", "mode_500");
            changeAuthServerMode("failing_userinfo", "mode_503");

            String topic = "RetriesTests-plainIntrospectAndUserinfoEndpointsRetriesTest";

            // try to produce
            long start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message")).get();

            // check that at least 9 seconds have passed - 3 for token retry, 3 for introspect retry, and 3 for userinfo retry
            // due to retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assert.assertTrue("It should take at least 9 seconds to complete", System.currentTimeMillis() - start > 3 * PAUSE_MILLIS);
        }
    }

    private void testClientRetries() throws Exception {
        System.out.println("    ====    Check that Kafka client connecting to token endpoint uses http.retries config");

        final String kafkaBootstrap = "kafka:9096";
        final String hostPort = "mockoauth:8090";

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
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        try {
            new KafkaProducer<>(producerProps);

            Assert.fail("Should fail with KafkaException");
        } catch (Exception e) {
            // token endpoint is tried and fails
            // get the exception

            Assert.assertTrue("is instanceof KafkaException", e instanceof KafkaException);
            Assert.assertTrue("Failed due to LoginException", e.getCause().toString().contains("LoginException"));
        }

        // repeat, it should succeed
        KafkaProducer<String, String> p = new KafkaProducer<>(producerProps);
        p.close();

        // now create a new producer with http.retries = 1 and some pause
        oauthConfig.put(Config.OAUTH_HTTP_RETRIES, "1");
        oauthConfig.put(Config.OAUTH_HTTP_RETRY_PAUSE_MILLIS, String.valueOf(PAUSE_MILLIS));

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // create producer
        long start = System.currentTimeMillis();
        p = new KafkaProducer<>(producerProps);

        // token endpoint is tried and fails
        // should automatically retry and succeed
        Assert.assertTrue("It should take at least 3 seconds to get a token", System.currentTimeMillis() - start > PAUSE_MILLIS);
        p.close();
    }

    private void testPlainRetriesWithJWKS() throws Exception {
        System.out.println("    ====    Check that broker connecting to token endpoint on PLAIN with configured JWKS endpoint uses http.retries config");

        // Use FAILINGJWT kafka listener that uses /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = "kafka:9098";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);
        createOAuthClient("kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);


        // set jwks endpoint to operate properly
        changeAuthServerMode("jwks", "mode_200");

        long matchedCount = 0;
        long start = System.currentTimeMillis();
        // wait for a maximum of 10 seconds for FAILINGJWT listener to refresh the keys
        for (int i = 0; matchedCount == 0 && System.currentTimeMillis() - start <= JWKS_REFRESH_PERIOD_MILLIS; i++) {
            if (i > 0) {
                Thread.sleep(1000);
            }

            List<String> log = getContainerLogsForString(kafkaContainer, "Response body");
            matchedCount = log.stream().filter(s -> s.contains("https://mockoauth:8090/jwks")).count();
        }

        Assert.assertTrue("Detected JWKS keys refresh success within 10 seconds?", matchedCount > 0);

        // set failing_token endpoint to always return 500, so that the retry will fail
        changeAuthServerMode("failing_token", "mode_failing_500");


        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            String topic = "RetriesTests-plainRetriesWithJWKSTest";

            // try to produce
            start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message")).get();
                Assert.fail("Should have failed due to failing_token set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assert.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            long diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assert.assertTrue("It should take at least 3 seconds to fail (" + diff + ")", diff > PAUSE_MILLIS);


            // configure failing_token endpoint so that they only fail every other time
            changeAuthServerMode("failing_token", "mode_400");

            start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message")).get();

            diff = System.currentTimeMillis() - start;
            // check that at least 3 seconds have passed - due to token retry
            Assert.assertTrue("It should take at least 3 seconds due to token retry (" + diff + ")", diff > PAUSE_MILLIS);
        }
    }

    private void testIntrospectAndUserinfoEndpointsRetries() throws Exception {
        System.out.println("    ====    Check that broker connecting to introspection and userinfo endpoints on OAUTHBEARER uses http.retries config");

        // use kafka listener that uses /failing_introspect and failing_userinfo
        final String kafkaBootstrap = "kafka:9097";
        final String hostPort = "mockoauth:8090";
        final String tokenEndpointUri = "https://" + hostPort + "/token";

        String testClient = "testclient";
        String testSecret = "testsecret";
        createOAuthClient(testClient, testSecret);

        createOAuthClient("kafka", "kafka-secret");

        // authenticate oauth with accesstoken
        changeAuthServerMode("token", "mode_200");
        String accessToken = loginWithClientSecret(tokenEndpointUri, testClient, testSecret, "../docker/target/kafka/certs/ca-truststore.p12", "changeit");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, accessToken);
        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // set failing_introspect endpoint to always return 500, so that the retry will fail
        changeAuthServerMode("failing_introspect", "mode_failing_500");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            String topic = "RetriesTests-introspectAndUserinfoEndpointsRetriesTest";

            // try to produce
            long start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message")).get();
                Assert.fail("Should have failed due to failing_introspect set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assert.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            // check that at least 3 seconds have passed - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assert.assertTrue("It should take at least 3 seconds to fail", System.currentTimeMillis() - start > PAUSE_MILLIS);


            // set failing_introspect to mode_400, so it will fail but will be immediately retried and will succeed
            changeAuthServerMode("failing_introspect", "mode_400");

            // set failing_userinfo to mode_failing_500 so that a retry will fail
            changeAuthServerMode("failing_userinfo", "mode_failing_500");

            // authenticate oauth
            start = System.currentTimeMillis();
            try {
                producer.send(new ProducerRecord<>(topic, "The Message")).get();
                Assert.fail("Should have failed due to failing_introspect set to always return 500");
            } catch (ExecutionException e) {
                // fails
                if (!(e.getCause() instanceof SaslAuthenticationException)) {
                    Assert.fail("Should have failed with AuthenticationException but was " + e.getCause());
                }
            }

            // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assert.assertTrue("It should take at least 6 seconds to fail", System.currentTimeMillis() - start > 2 * PAUSE_MILLIS);


            // set failing_userinfo to mode_500 so that both failing_introspect and failing_userinfo can recover
            changeAuthServerMode("failing_userinfo", "mode_500");

            // authenticate oauth
            start = System.currentTimeMillis();
            producer.send(new ProducerRecord<>(topic, "The Message")).get();

            // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
            Assert.assertTrue("It should take at least 6 seconds for double recovery", System.currentTimeMillis() - start > 2 * PAUSE_MILLIS);
        }
    }
}
