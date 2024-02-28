/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.testsuite.oauth.common.TestUtil;
import org.apache.kafka.clients.admin.AdminClient;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

@SuppressFBWarnings({"THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION", "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT"})
public class Common {

    private static final Logger log = LoggerFactory.getLogger(Common.class);

    static final String HOST = "keycloak";
    static final String REALM = "kafka-authz";
    static final String TOKEN_ENDPOINT_URI = "http://" + HOST + ":8080/realms/" + REALM + "/protocol/openid-connect/token";

    private static final String PLAIN_LISTENER = "kafka:9100";
    static final String TEAM_A_CLIENT = "team-a-client";
    static final String TEAM_B_CLIENT = "team-b-client";
    static final String BOB = "bob";
    static final String ZERO = "zero";

    static final String TOPIC_A = "a_messages";
    static final String TOPIC_B = "b_messages";
    static final String TOPIC_X = "x_messages";


    final String kafkaBootstrap;

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
        tokens.put(TEAM_A_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(TEAM_B_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(BOB, loginWithUsernamePassword(URI.create(TOKEN_ENDPOINT_URI),
                BOB, BOB + "-password", "kafka-cli"));
        tokens.put(ZERO, loginWithUsernamePassword(URI.create(TOKEN_ENDPOINT_URI),
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

        Assert.assertTrue("Got message", records.count() >= 1);
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

            Assert.fail("Should fail with TopicAuthorizationException");
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
            Assert.fail("Should not be able to send message");
        } catch (ExecutionException e) {
            // should get authorization exception
            Assert.assertTrue("Should fail with TopicAuthorizationException", e.getCause() instanceof TopicAuthorizationException);
        }
    }

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
        try (AdminClient adminClient = buildAdminClientForPlain(PLAIN_LISTENER, "admin")) {

            TestUtil.waitForCondition(() -> {
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
            }, 500, 210);
        }
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
            ? buildConsumerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(name))
            : buildConsumerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(name));
    }

    Properties buildConsumerConfig(String kafkaBootstrap, boolean usePlain, String clientId, String secret) {
        return usePlain ?
                buildConsumerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(clientId, secret)) :
                buildConsumerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(clientId));
    }

    Map<String, String> buildAuthConfigForOAuthBearer(String name) {
        Map<String, String> config = new HashMap<>();

        String token = tokens.get(name);
        Assert.assertNotNull("No token for user: " + name + ". Was the user authenticated?", token);

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

    static String getJaasConfigOptionsString(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent: options.entrySet()) {
            sb.append(" ").append(ent.getKey()).append("=\"").append(ent.getValue()).append("\"");
        }
        return sb.toString();
    }

    public static Properties buildProducerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
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

    static Properties buildConsumerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    static Properties buildCommonConfigOAuthBearer(Map<String, String> oauthConfig) {
        String configOptions = getJaasConfigOptionsString(oauthConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " + configOptions + " ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        return p;
    }

    Properties buildProducerConfig(String kafkaBootstrap, boolean usePlain, String clientId, String secret) {
        return usePlain ?
                buildProducerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(clientId, secret)) :
                buildProducerConfigOAuthBearer(kafkaBootstrap, buildAuthConfigForOAuthBearer(clientId));
    }

    public static Properties buildProducerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildProducerConfigScram(String kafkaBootstrap, Map<String, String> scramConfig) {
        Properties p = buildCommonConfigScram(scramConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    static Properties buildConsumerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    static void setCommonConsumerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    }

    static Properties buildCommonConfigPlain(Map<String, String> plainConfig) {
        String configOptions = getJaasConfigOptionsString(plainConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "PLAIN");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " + configOptions + " ;");
        return p;
    }

    static Properties buildCommonConfigScram(Map<String, String> scramConfig) {
        String configOptions = getJaasConfigOptionsString(scramConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required " + configOptions + " ;");
        return p;
    }

    public static AdminClient buildAdminClientForPlain(String kafkaBootstrap, String user) {
        Properties adminProps = buildProducerConfigPlain(kafkaBootstrap, buildAuthConfigForPlain(user, user + "-password"));
        return AdminClient.create(adminProps);
    }

    void cleanup() {
        Properties bobAdminProps = buildAdminConfigForAccount(BOB);
        try (AdminClient admin = AdminClient.create(bobAdminProps)) {
            admin.deleteTopics(Arrays.asList(TOPIC_A, TOPIC_B, TOPIC_X, "non-existing-topic"));
            admin.deleteConsumerGroups(Arrays.asList(groupFor(TOPIC_A), groupFor(TOPIC_B), groupFor(TOPIC_X), groupFor("non-existing-topic")));
        }
    }
}
