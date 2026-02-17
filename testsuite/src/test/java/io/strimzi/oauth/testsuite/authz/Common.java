/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.oauth.testsuite.common.TestUtil;
import io.strimzi.oauth.testsuite.utils.KafkaClientConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressFBWarnings({"THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION", "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT"})
public class Common {

    private static final Logger log = LoggerFactory.getLogger(Common.class);

    static final String REALM = "kafka-authz";

    static String keycloakHostPort() {
        return System.getProperty("keycloak.host", "localhost") + ":" + System.getProperty("keycloak.port", "8080");
    }

    static String tokenEndpointUri() {
        return "http://" + keycloakHostPort() + "/realms/" + REALM + "/protocol/openid-connect/token";
    }

    private static final String PLAIN_LISTENER = "localhost:9100";
    static final String TEAM_A_CLIENT = "team-a-client";
    static final String TEAM_B_CLIENT = "team-b-client";
    static final String BOB = "bob";
    static final String ZERO = "zero";

    static final String TOPIC_A = "a_messages";
    static final String TOPIC_B = "b_messages";
    static final String TOPIC_X = "x_messages";


    String kafkaBootstrap;

    boolean usePlain;

    HashMap<String, String> tokens = new HashMap<>();

    Producer<String, String> teamAProducer;
    Consumer<String, String> teamAConsumer;

    Producer<String, String> teamBProducer;
    Consumer<String, String> teamBConsumer;

    Common(String kafkaBootstrap, boolean oauthOverPlain) {
        this.kafkaBootstrap = kafkaBootstrap;
        this.usePlain = oauthOverPlain;
    }

    static void produceToTopic(String topic, Properties config) throws Exception {

        try (Producer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }
    }

    void authenticateAllActors() throws IOException {
        tokens.put(TEAM_A_CLIENT, loginWithClientSecret(URI.create(tokenEndpointUri()), null, null,
                TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(TEAM_B_CLIENT, loginWithClientSecret(URI.create(tokenEndpointUri()), null, null,
                TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(BOB, loginWithUsernamePassword(URI.create(tokenEndpointUri()),
                BOB, BOB + "-password", "kafka-cli"));
        tokens.put(ZERO, loginWithUsernamePassword(URI.create(tokenEndpointUri()),
                ZERO, ZERO + "-password", "kafka-cli"));
    }

    static void consume(Consumer<String, String> consumer, String topic) {
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }

        consumer.seekToBeginning(Collections.singletonList(partition));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertTrue(records.count() >= 1, "Got message");
    }

    static void consumeFail(Consumer<String, String> consumer, String topic) {
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(partition));

        try {
            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }

            consumer.seekToBeginning(Collections.singletonList(partition));
            consumer.poll(Duration.ofSeconds(1));

            fail("Should fail with TopicAuthorizationException");
        } catch (TopicAuthorizationException expected) {
            // ignored
        }
    }

    static void produce(Producer<String, String> producer, String topic) throws InterruptedException, ExecutionException {
        producer.send(new ProducerRecord<>(topic, "The Message")).get();
    }

    static void produceFail(Producer<String, String> producer, String topic) throws InterruptedException {
        try {
            produce(producer, topic);
            fail("Should not be able to send message");
        } catch (ExecutionException e) {
            assertInstanceOf(TopicAuthorizationException.class, e.getCause(), "Should fail with TopicAuthorizationException");
        }
    }

    /**
     * Login with username and password using client_id in the request body (not Basic Auth).
     * This is the authz-specific variant needed by Keycloak authorization tests.
     */
    public static String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

        String body = "grant_type=password&username=" + urlencode(username) +
                "&password=" + urlencode(password) + "&client_id=" + urlencode(clientId);

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                null,
                "application/x-www-form-urlencoded",
                body,
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }


    public static void waitForACLs() throws Exception {

        // Create admin client using user `admin:admin-password` over PLAIN listener (port 9100)
        TestUtil.waitForCondition(() -> {
            try (AdminClient adminClient = buildAdminClientForPlain(PLAIN_LISTENER, "admin")) {
                try {
                    Collection<AclBinding> result = adminClient.describeAcls(new AclBindingFilter(ResourcePatternFilter.ANY,
                            new AccessControlEntryFilter("User:alice", null, AclOperation.IDEMPOTENT_WRITE, AclPermissionType.ALLOW))).values().get();
                    for (AclBinding acl : result) {
                        if (AclOperation.IDEMPOTENT_WRITE.equals(acl.entry().operation())) {
                            return true;
                        }
                    }
                    return false;

                } catch (Throwable e) {
                    throw new RuntimeException("ACLs for User:alice could not be retrieved: ", e);
                }
            }
        }, 2000, 210);
    }

    Producer<String, String> getProducer(final String name) {
        return recycleProducer(name, true);
    }

    Producer<String, String> newProducer(final String name) {
        return recycleProducer(name, false);
    }

    Producer<String, String> recycleProducer(final String name, boolean recycle) {
        switch (name) {
            case TEAM_A_CLIENT:
                if (teamAProducer != null) {
                    if (recycle) {
                        return teamAProducer;
                    } else {
                        teamAProducer.close();
                    }
                }
                break;
            case TEAM_B_CLIENT:
                if (teamBProducer != null) {
                    if (recycle) {
                        return teamBProducer;
                    } else {
                        teamBProducer.close();
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported producer: " + name);
        }

        Properties producerProps = buildProducerConfigForAccount(name);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        if (TEAM_A_CLIENT.equals(name)) {
            teamAProducer = producer;
        } else {
            teamBProducer = producer;
        }
        return producer;
    }

    Consumer<String, String> newConsumer(final String name, String topic) {
        switch (name) {
            case TEAM_A_CLIENT:
                if (teamAConsumer != null) {
                    teamAConsumer.close();
                }
                break;
            case TEAM_B_CLIENT:
                if (teamBConsumer != null) {
                    teamBConsumer.close();
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported consumer: " + name);
        }

        Properties consumerProps = buildConsumerConfigForAccount(name);
        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(topic));
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        if (TEAM_A_CLIENT.equals(name)) {
            teamAConsumer = consumer;
        } else {
            teamBConsumer = consumer;
        }
        return consumer;
    }

    Properties buildAdminConfigForAccount(String name) {
        return buildProducerConfigForAccount(name);
    }

    Properties buildProducerConfigForAccount(String name) {
        return usePlain
            ? buildProducerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(name))
            : buildProducerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(name));
    }

    Properties buildConsumerConfigForAccount(String name) {
        return usePlain
            ? KafkaClientConfig.buildConsumerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(name))
            : KafkaClientConfig.buildConsumerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(name));
    }

    Properties buildConsumerConfig(String kafkaBootstrap, boolean usePlain, String clientId, String secret) {
        return usePlain ?
                KafkaClientConfig.buildConsumerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(clientId, secret)) :
                KafkaClientConfig.buildConsumerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(clientId));
    }

    Map<String, String> buildAuthConfigForOAuthBearer(String name) {
        Map<String, String> config = new HashMap<>();

        String token = tokens.get(name);
        assertNotNull(token, "No token for user: " + name + ". Was the user authenticated?");

        config.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        return config;
    }

    Map<String, String> buildAuthConfigForPlain(String name) {
        return name.endsWith("-client")
                ? buildAuthConfigForPlain(name, name + "-secret")
                : buildAuthConfigForPlain(name, "$accessToken:" + tokens.get(name));
    }

    static Map<String, String> buildAuthConfigForPlain(String clientId, String secret) {
        Map<String, String> config = new HashMap<>();
        config.put("username", clientId);
        config.put("password", secret);
        return config;
    }

    static String groupFor(String topic) {
        return topic + "-group";
    }

    // Authz-specific producer config with RETRIES=10 and MAX_BLOCK_MS=300000
    public static Properties buildProducerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = KafkaClientConfig.buildCommonConfigOAuthBearer(oauthConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    static void setCommonProducerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        p.setProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        p.setProperty(ProducerConfig.RETRIES_CONFIG, "10");
        p.setProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "300000");
    }

    Properties buildProducerConfig(String kafkaBootstrap, boolean usePlain, String clientId, String secret) {
        return usePlain ?
                buildProducerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(clientId, secret)) :
                buildProducerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(clientId));
    }

    public static Properties buildProducerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = KafkaClientConfig.buildCommonConfigPlain(plainConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildProducerConfigScram(String kafkaBootstrap, Map<String, String> scramConfig) {
        Properties p = KafkaClientConfig.buildCommonConfigScram(scramConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static AdminClient buildAdminClientForPlain(String kafkaBootstrap, String user) {
        Properties adminProps = buildProducerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(user, user + "-password"));
        return AdminClient.create(adminProps);
    }

    void cleanup() {
        closeClients();
        Properties bobAdminProps = buildAdminConfigForAccount(BOB);
        try (AdminClient admin = AdminClient.create(bobAdminProps)) {
            admin.deleteTopics(Arrays.asList(TOPIC_A, TOPIC_B, TOPIC_X, "non-existing-topic"));
            admin.deleteConsumerGroups(Arrays.asList(groupFor(TOPIC_A), groupFor(TOPIC_B), groupFor(TOPIC_X), groupFor("non-existing-topic")));
        }
    }

    private void closeClients() {
        if (teamAProducer != null) {
            teamAProducer.close();
            teamAProducer = null;
        }
        if (teamBProducer != null) {
            teamBProducer.close();
            teamBProducer = null;
        }
        if (teamAConsumer != null) {
            teamAConsumer.close();
            teamAConsumer = null;
        }
        if (teamBConsumer != null) {
            teamBConsumer.close();
            teamBConsumer = null;
        }
    }

    void doTestTeamAClientPart1() throws Exception {
        Producer<String, String> teamAProducer = getProducer(TEAM_A_CLIENT);

        produceFail(teamAProducer, TOPIC_B);

        teamAProducer = newProducer(TEAM_A_CLIENT);

        produce(teamAProducer, TOPIC_A);

        produceFail(teamAProducer, TOPIC_X);

        Consumer<String, String> teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_B);

        consumeFail(teamAConsumer, TOPIC_B);

        teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_A);

        consume(teamAConsumer, TOPIC_A);

        consumeFail(teamAConsumer, TOPIC_X);
    }

    void doTestTeamBClientPart1() throws Exception {
        Producer<String, String> teamBProducer = getProducer(TEAM_B_CLIENT);

        produceFail(teamBProducer, TOPIC_A);

        teamBProducer = newProducer(TEAM_B_CLIENT);

        produce(teamBProducer, TOPIC_B);

        produceFail(teamBProducer, TOPIC_X);

        Consumer<String, String> teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_A);

        consumeFail(teamBConsumer, TOPIC_A);

        teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_B);

        consume(teamBConsumer, TOPIC_B);
    }

    void doCreateTopicAsClusterManager() throws Exception {
        Properties bobAdminProps = buildAdminConfigForAccount(BOB);
        try (AdminClient admin = AdminClient.create(bobAdminProps)) {
            admin.createTopics(singletonList(new NewTopic(TOPIC_X, 1, (short) 1))).all().get();
        }
    }

    void doTestTeamAClientPart2() throws Exception {
        Producer<String, String> teamAProducer = newProducer(TEAM_A_CLIENT);

        produce(teamAProducer, TOPIC_X);

        Consumer<String, String> teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_A);
        consumeFail(teamAConsumer, TOPIC_X);
    }

    void doTestTeamBClientPart2() throws Exception {
        Consumer<String, String> teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_B);
        consume(teamBConsumer, TOPIC_X);

        Producer<String, String> teamBProducer = newProducer(TEAM_B_CLIENT);
        produceFail(teamBProducer, TOPIC_X);
    }

    void doTestClusterManager() throws Exception {
        Properties bobAdminProps = buildProducerConfigForAccount(BOB);
        try (Producer<String, String> producer = new KafkaProducer<>(bobAdminProps)) {

            Properties consumerProps = buildConsumerConfigForAccount(BOB);
            try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {

                produce(producer, TOPIC_X);
                produce(producer, TOPIC_A);
                produce(producer, TOPIC_B);
                produce(producer, "non-existing-topic");

                consume(consumer, TOPIC_X);
                consume(consumer, TOPIC_A);
                consume(consumer, TOPIC_B);
                consume(consumer, "non-existing-topic");
            }
        }
    }

    void doTestUserWithNoPermissions(GenericContainer<?> kafkaContainer) throws Exception {
        Properties producerProps = buildProducerConfigForAccount(ZERO);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {

            Properties consumerProps = buildConsumerConfigForAccount(ZERO);
            try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {

                produceFail(producer, TOPIC_X);
                produceFail(producer, TOPIC_A);
                produceFail(producer, TOPIC_B);
                produceFail(producer, "non-existing-topic");

                consumeFail(consumer, TOPIC_X);
                consumeFail(consumer, TOPIC_A);
                consumeFail(consumer, TOPIC_B);
                consumeFail(consumer, "non-existing-topic");
            }
        }

        List<String> lines = TestUtil.getContainerLogsForString(kafkaContainer, "Saving non-null grants for user: zero");
        assertEquals(1, lines.size(), "Saved non-null grants");

        lines = TestUtil.getContainerLogsForString(kafkaContainer, "Got grants for 'OAuthKafkaPrincipal(User:zero,");
        assertTrue(lines.size() > 0, "Grants for user are: {}");

        for (String line: lines) {
            assertTrue(line.contains(": {}"), "Grants for user are: {}");
        }
    }
}
