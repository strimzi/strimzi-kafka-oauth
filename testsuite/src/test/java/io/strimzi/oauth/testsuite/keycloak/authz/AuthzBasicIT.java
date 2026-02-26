/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.ConcurrentKafkaClientsRunner;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.test.container.AuthenticationType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildConsumerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildConsumerConfigPlain;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigPlain;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.consume;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Basic authorization tests for Keycloak with KRaft
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "kafka-authz",
        setupAcls = true,
        oauthProperties = {
            "oauth.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.groups.claim=$.realm_access.roles",
            "oauth.fallback.username.claim=username",
            "unsecuredLoginStringClaim_sub=admin"
        },
        kafkaProperties = {
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
            "super.users=User:admin;User:service-account-kafka"
        }
    )
)
public class AuthzBasicIT extends AbstractAuthzIT {

    private static final Logger log = LoggerFactory.getLogger(AuthzBasicIT.class);

    @BeforeAll
    void setUp() throws Exception {
        authenticateAllActors();
    }

    @AfterAll
    void tearDown() {
        Properties bobProps = buildProducerConfigOAuthBearer(env.getBootstrapServers(),
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(BOB)), AUTHZ_RETRIES);
        cleanup(bobProps);
    }

    @Test
    @Tag(TestTags.AUTHORIZATION)
    public void testAuthorization() throws Exception {
        String bootstrap = env.getBootstrapServers();

        Properties teamAProducer = buildProducerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(TEAM_A_CLIENT)), AUTHZ_RETRIES);
        Properties teamAConsumer = buildConsumerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(TEAM_A_CLIENT)));

        Properties teamBProducer = buildProducerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(TEAM_B_CLIENT)), AUTHZ_RETRIES);
        Properties teamBConsumer = buildConsumerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(TEAM_B_CLIENT)));

        Properties bobProducer = buildProducerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(BOB)), AUTHZ_RETRIES);
        Properties bobConsumer = buildConsumerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(BOB)));

        Properties zeroProducer = buildProducerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(ZERO)), AUTHZ_RETRIES);
        Properties zeroConsumer = buildConsumerConfigOAuthBearer(bootstrap,
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, getToken(ZERO)));

        verifyTeamACanOnlyAccessOwnTopics(teamAProducer, teamAConsumer);
        verifyTeamBCanOnlyAccessOwnTopics(teamBProducer, teamBConsumer);
        createSharedTopicAsClusterManager(bobProducer);
        verifyTeamACanProduceToSharedTopic(teamAProducer, teamAConsumer);
        verifyTeamBCanConsumeFromSharedTopic(teamBConsumer, teamBProducer);
        verifyClusterManagerCanAccessAllTopics(bobProducer, bobConsumer);
        verifyUserWithNoPermissionsIsDenied(zeroProducer, zeroConsumer, env.getKafka());
    }

    @Test
    @Tag(TestTags.CONFIGURATION)
    public void verifyAuthorizerConfiguration() {
        List<String> lines = getContainerLogsForString(env.getKafka(), "Configured KeycloakRBACAuthorizer");
        assertTrue(lines.size() > 0, "Kafka log should contain string: 'KeycloakRBACAuthorizer'");

        String value = getLoggerAttribute(lines, "connectTimeoutSeconds");
        assertEquals("20", value, "'connectTimeoutSeconds' should be 20");

        value = getLoggerAttribute(lines, "readTimeoutSeconds");
        assertEquals("45", value, "'readTimeoutSeconds' should be 45");

        value = getLoggerAttribute(lines, "enableMetrics");
        assertEquals("true", value, "'enableMetrics' should be true");

        value = getLoggerAttribute(lines, "httpRetries");
        assertEquals("1", value, "'httpRetries' should be 1");

        value = getLoggerAttribute(lines, "reuseGrants");
        assertEquals("true", value, "'reuseGrants' should be true");
    }

    /**
     * Ensure that multiple instantiated KeycloakAuthorizers share a single instance of KeycloakRBACAuthorizer
     */
    @Test
    @Tag(TestTags.SINGLETON)
    @Tag(TestTags.AUTHORIZATION)
    public void testKeycloakAuthorizerSingleton() {
        // In KRaft mode, there are 3 KeycloakAuthorizer instances
        int keycloakAuthorizersCount = 2;

        List<String> lines = env.getKafka().getLogs().lines()
            .toList();
        List<String> keycloakAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakAuthorizer@"))
                .collect(Collectors.toList());
        List<String> keycloakRBACAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakRBACAuthorizer@"))
                .collect(Collectors.toList());

        assertEquals(keycloakAuthorizersCount, keycloakAuthorizerLines.size(), "Configured KeycloakAuthorizer");
        assertEquals(1, keycloakRBACAuthorizerLines.size(), "Configured KeycloakRBACAuthorizer");
    }

    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     * <p>
     * It connects concurrently with multiple producers with different client IDs using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     * With KeycloakRBACAuthorizer configured, any mixup of the credentials between different clients will be caught as
     * AuthorizationException would be thrown trying to write to the topic if the user context was mismatched.
     */
    @Test
    @Tag(TestTags.AUTHORIZATION)
    @Tag(TestTags.CONCURRENT)
    @Tag(TestTags.FLOOD)
    public void clientCredentialsWithFloodTest() throws Exception {
        String producerPrefix = "kafka-producer-client-";
        String consumerPrefix = "kafka-consumer-client-";

        // 10 parallel producers and consumers
        final int clientCount = 10;

        HashMap<String, String> floodTokens = new HashMap<>();
        for (int i = 1; i <= clientCount; i++) {
            obtainAndStoreFloodToken(producerPrefix, floodTokens, i);
            obtainAndStoreFloodToken(consumerPrefix, floodTokens, i);
        }

        // Try write to the mismatched topic - we should get AuthorizationException
        try {
            Properties props = buildProducerConfigOAuthBearer(env.getBootstrapServers(),
                Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, floodTokens.get("kafka-producer-client-1")), AUTHZ_RETRIES);
            produceMessage(props, "messages-2", "Message 0");
            fail("Sending to 'messages-2' using 'kafka-producer-client-1' should fail with AuthorizationException");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthorizationException.class, e.getCause(), "Exception type should be AuthorizationException");
        }

        ConcurrentKafkaClientsRunner runner = new ConcurrentKafkaClientsRunner();

        // Do 5 iterations - each time hitting the broker with 10 parallel requests
        for (int run = 0; run < 5; run++) {
            log.info("*** Run {}/5", run + 1);
            for (int i = 1; i <= clientCount; i++) {
                String topic = "messages-" + i;
                addProducerTask(runner, producerPrefix + i, topic, floodTokens);
                addConsumerTask(runner, consumerPrefix + i, topic, groupForConsumer(i), floodTokens);
            }

            runner.executeAll(60);
            runner.clear();
        }

        // Now try the same with a single topic
        for (int run = 0; run < 5; run++) {

            for (int i = 1; i <= clientCount; i++) {
                String topic = "messages-1";
                addProducerTask(runner, producerPrefix + "1", topic, floodTokens);
                addConsumerTask(runner, consumerPrefix + "1", topic, groupForConsumer(1), floodTokens);
            }

            runner.executeAll(60);
            runner.clear();
        }
    }

    @KafkaConfig(
        realm = "kafka-authz",
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        setupAcls = true,
        oauthProperties = {
            "oauth.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
            "oauth.jwks.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        },
        kafkaProperties = {
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
            "super.users=User:admin;User:service-account-kafka"
        }
    )
    @Test
    @Tag(TestTags.AUTHORIZATION)
    public void testAuthorizationOverPlain() throws Exception {
        authenticateAllActors();
        String bootstrap = env.getBootstrapServers();

        Properties teamAProducer = buildProducerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret"), AUTHZ_RETRIES);
        Properties teamAConsumer = buildConsumerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret"));

        Properties teamBProducer = buildProducerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret"), AUTHZ_RETRIES);
        Properties teamBConsumer = buildConsumerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret"));

        Properties bobProducer = buildProducerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(BOB, "$accessToken:" + getToken(BOB)), AUTHZ_RETRIES);
        Properties bobConsumer = buildConsumerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(BOB, "$accessToken:" + getToken(BOB)));

        Properties zeroProducer = buildProducerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(ZERO, "$accessToken:" + getToken(ZERO)), AUTHZ_RETRIES);
        Properties zeroConsumer = buildConsumerConfigPlain(bootstrap,
            KafkaClientsConfig.buildAuthConfigForPlain(ZERO, "$accessToken:" + getToken(ZERO)));

        try {
            verifyTeamACanOnlyAccessOwnTopics(teamAProducer, teamAConsumer);
            verifyTeamBCanOnlyAccessOwnTopics(teamBProducer, teamBConsumer);
            createSharedTopicAsClusterManager(bobProducer);
            verifyTeamACanProduceToSharedTopic(teamAProducer, teamAConsumer);
            verifyTeamBCanConsumeFromSharedTopic(teamBConsumer, teamBProducer);
            verifyClusterManagerCanAccessAllTopics(bobProducer, bobConsumer);
            verifyUserWithNoPermissionsIsDenied(zeroProducer, zeroConsumer, env.getKafka());
        } finally {
            cleanup(bobProducer);
        }
    }

    private static String getLoggerAttribute(List<String> lines, String name) {
        for (String line: lines) {
            if (line.contains(name)) {
                String[] keyVal = line.split(":");
                return keyVal[1].trim().split(" ")[0].trim();
            }
        }
        return null;
    }

    private void addProducerTask(ConcurrentKafkaClientsRunner runner, String clientId,
                                 String topic, Map<String, String> tokenMap) {
        Properties props = buildProducerConfigOAuthBearer(env.getBootstrapServers(),
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, tokenMap.get(clientId)), AUTHZ_RETRIES);
        runner.addTask(() -> {
            produceMessage(props, topic, "Message 0");
            log.debug("[{}] Produced message to '{}'", clientId, topic);
            return null;
        });
    }

    private void addConsumerTask(ConcurrentKafkaClientsRunner runner, String clientId,
                                 String topic, String group, Map<String, String> tokenMap) {
        Properties props = buildConsumerConfigOAuthBearer(env.getBootstrapServers(),
            Map.of(ClientConfig.OAUTH_ACCESS_TOKEN, tokenMap.get(clientId)));
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, group);
        runner.addTask(() -> {
            for (int triesLeft = 300; triesLeft > 0; triesLeft--) {
                try {
                    consume(props, topic);
                    log.debug("[{}] Consumed message from '{}'", clientId, topic);
                    break;
                } catch (Throwable t) {
                    if (triesLeft <= 1) {
                        throw t;
                    }
                    Thread.sleep(100);
                }
            }
            return null;
        });
    }

    private String groupForConsumer(int index) {
        return "g" + (index < 10 ? index : 0);
    }

    private void obtainAndStoreFloodToken(String prefix, HashMap<String, String> tokens, int i) throws IOException {
        String clientId = prefix + i;
        String secret = clientId + "-secret";

        tokens.put(clientId, loginWithClientSecret(URI.create(env.getTokenEndpointUri()), null, null,
            clientId, secret, true, null, null, true).token());
    }
}
