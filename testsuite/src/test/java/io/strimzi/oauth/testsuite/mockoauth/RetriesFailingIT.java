/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.mockoauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlain;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithClientSecret;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static io.strimzi.oauth.testsuite.utils.TestUtil.waitForCondition;

/**
 * Tests for HTTP retry handling on failing listeners.
 * Validates that broker-side HTTP retries work correctly for introspection, userinfo, token, and JWKS endpoints.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        oauthProperties = {
            "oauth.config.id=FAILINGINTROSPECT",
            "oauth.introspection.endpoint.uri=https://mockoauth:8090/failing_introspect",
            "oauth.userinfo.endpoint.uri=https://mockoauth:8090/failing_userinfo",
            "oauth.username.claim=uid",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "oauth.http.retries=1",
            "oauth.http.retry.pause.millis=3000",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class RetriesFailingIT {
    // FAILINGINTROSPECT and FAILINGJWT listeners are configured with 'oauth.http.retry.pause.millis' of 3000
    static final int PAUSE_MILLIS = 3000;

    OAuthEnvironmentExtension env;

    @KafkaConfig(
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        oauthProperties = {
            "oauth.config.id=FAILINGINTROSPECT",
            "oauth.token.endpoint.uri=https://mockoauth:8090/failing_token",
            "oauth.introspection.endpoint.uri=https://mockoauth:8090/failing_introspect",
            "oauth.userinfo.endpoint.uri=https://mockoauth:8090/failing_userinfo",
            "oauth.username.claim=uid",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "oauth.http.retries=1",
            "oauth.http.retry.pause.millis=3000",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    @Test
    @Tag(TestTags.RETRY)
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.INTROSPECTION)
    void testPlainIntrospectAndUserinfoEndpointsRetries() throws Exception {
        // Use FAILINGINTROSPECT kafka listener that uses /failing_introspect, /failing_userinfo,
        // and /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = env.getBootstrapServers();

        String testClient = "testclient";
        String testSecret = "testsecret";
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), testClient, testSecret);
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), "kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);

        // configure the endpoints so that they are in failing mode
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_token", "mode_400");
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_introspect", "mode_500");
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_userinfo", "mode_503");

        String topic = "RetriesTests-plainIntrospectAndUserinfoEndpointsRetriesTest";

        // try to produce
        long start = System.currentTimeMillis();
        produceMessage(producerProps, topic, "The Message");

        // check that at least 9 seconds have passed - 3 for token retry, 3 for introspect retry, and 3 for userinfo retry
        // due to retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
        Assertions.assertTrue(System.currentTimeMillis() - start > 3 * PAUSE_MILLIS, "It should take at least 9 seconds to complete");
    }

    @Test
    @Tag(TestTags.RETRY)
    @Tag(TestTags.INTROSPECTION)
    void testIntrospectAndUserinfoEndpointsRetries() throws Exception {
        // use kafka listener that uses /failing_introspect and failing_userinfo
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getMockOAuthHostPort();
        final String tokenEndpointUri = "https://" + hostPort + "/token";

        String testClient = "testclient";
        String testSecret = "testsecret";
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), testClient, testSecret);

        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), "kafka", "kafka-secret");

        // authenticate oauth with accesstoken
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "token", "mode_200");
        String accessToken = loginWithClientSecret(tokenEndpointUri, testClient, testSecret, "target/kafka/certs/ca-truststore.p12", "changeit");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, accessToken);
        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);

        // set failing_introspect endpoint to always return 500, so that the retry will fail
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_introspect", "mode_failing_500");

        String topic = "RetriesTests-introspectAndUserinfoEndpointsRetriesTest";

        // try to produce
        long start = System.currentTimeMillis();
        try {
            produceMessage(producerProps, topic, "The Message");
            Assertions.fail("Should have failed due to failing_introspect set to always return 500");
        } catch (ExecutionException e) {
            // fails
            if (!(e.getCause() instanceof SaslAuthenticationException)) {
                Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
            }
        }

        // check that at least 3 seconds have passed - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
        Assertions.assertTrue(System.currentTimeMillis() - start > PAUSE_MILLIS, "It should take at least 3 seconds to fail");


        // set failing_introspect to mode_400, so it will fail but will be immediately retried and will succeed
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_introspect", "mode_400");

        // set failing_userinfo to mode_failing_500 so that a retry will fail
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_userinfo", "mode_failing_500");

        // authenticate oauth
        start = System.currentTimeMillis();
        try {
            produceMessage(producerProps, topic, "The Message");
            Assertions.fail("Should have failed due to failing_introspect set to always return 500");
        } catch (ExecutionException e) {
            // fails
            if (!(e.getCause() instanceof SaslAuthenticationException)) {
                Assertions.fail("Should have failed with AuthenticationException but was " + e.getCause());
            }
        }

        // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
        Assertions.assertTrue(System.currentTimeMillis() - start > 2 * PAUSE_MILLIS, "It should take at least 6 seconds to fail");


        // set failing_userinfo to mode_500 so that both failing_introspect and failing_userinfo can recover
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_userinfo", "mode_500");

        // authenticate oauth
        start = System.currentTimeMillis();
        produceMessage(producerProps, topic, "The Message");

        // check that at least 6 seconds have passed due to two retries - the retry pause configured on the listener for 9097 (FAILINGINTROSPECT)
        Assertions.assertTrue(System.currentTimeMillis() - start > 2 * PAUSE_MILLIS, "It should take at least 6 seconds for double recovery");
    }

    @KafkaConfig(
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
    @Test
    @Tag(TestTags.RETRY)
    @Tag(TestTags.PLAIN)
    @Tag(TestTags.JWKS)
    void testPlainRetriesWithJWKS() throws Exception {
        // Wait for JWKS keys to be loaded (refresh interval is 10s, wait up to 30s)
        waitForCondition(() ->
                !getContainerLogsForString(env.getKafka(), "JWKS keys change detected").isEmpty(),
            1000, 30);

        // Use FAILINGJWT kafka listener that uses /failing_token, and supports OAuth over PLAIN
        final String kafkaBootstrap = env.getBootstrapServers();

        String testClient = "testclient";
        String testSecret = "testsecret";
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), testClient, testSecret);
        MockOAuthAdmin.createOAuthClient(env.getMockOAuthAdminHostPort(), "kafka", "kafka-secret");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put("username", testClient);
        oauthConfig.put("password", testSecret);

        Properties producerProps = buildProducerConfigPlain(kafkaBootstrap, oauthConfig);

        // JWKS keys are guaranteed loaded by waitForCondition above

        // set failing_token endpoint to always return 500, so that the retry will fail
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_token", "mode_failing_500");

        String topic = "RetriesTests-plainRetriesWithJWKSTest";

        // try to produce
        long start = System.currentTimeMillis();
        try {
            produceMessage(producerProps, topic, "The Message");
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
        MockOAuthAdmin.changeAuthServerMode(env.getMockOAuthAdminHostPort(), "failing_token", "mode_400");

        start = System.currentTimeMillis();
        produceMessage(producerProps, topic, "The Message");

        diff = System.currentTimeMillis() - start;
        // check that at least 3 seconds have passed - due to token retry
        Assertions.assertTrue(diff > PAUSE_MILLIS, "It should take at least 3 seconds due to token retry (" + diff + ")");
    }
}
