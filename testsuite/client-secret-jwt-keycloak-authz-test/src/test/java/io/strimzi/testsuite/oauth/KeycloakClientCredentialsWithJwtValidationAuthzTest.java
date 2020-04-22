/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import io.strimzi.kafka.oauth.common.HttpUtil;
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
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

@RunWith(Arquillian.class)
public class KeycloakClientCredentialsWithJwtValidationAuthzTest {

    private static final String HOST = "keycloak";
    private static final String REALM = "kafka-authz";
    private static final String TOKEN_ENDPOINT_URI = "http://" + HOST + ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

    private static final String TEAM_A_CLIENT = "team-a-client";
    private static final String TEAM_B_CLIENT = "team-b-client";
    private static final String BOB = "bob";

    private static final String TOPIC_A = "a_messages";
    private static final String TOPIC_B = "b_messages";
    private static final String TOPIC_X = "x_messages";


    private HashMap<String, String> tokens;

    private Producer<String, String> teamAProducer;
    private Consumer<String, String> teamAConsumer;

    private Producer<String, String> teamBProducer;
    private Consumer<String, String> teamBConsumer;


    @Test
    public void doTest() throws Exception {

        System.out.println("==== KeycloakClientCredentialsWithJwtValidationAuthzTest - Tests Authorization ====");

        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        defaults.setProperty(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties p = System.getProperties();
        for (Object key: p.keySet()) {
            System.out.println("" + key + "=" + p.get(key));
        }

        fixBadlyImportedAuthzSettings();

        tokens = authenticateAllActors();

        testTeamAClientPart1();

        testTeamBClientPart1();

        createTopicAsClusterManager();

        testTeamAClientPart2();

        testTeamBClientPart2();

        testClusterManager();
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

        Properties producerProps = buildProducerConfig(tokens.get(name));
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

        Properties consumerProps = buildConsumerConfig(tokens.get(name));
        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(topic));
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        if (TEAM_A_CLIENT.equals(name)) {
            teamAConsumer = consumer;
        } else {
            teamBConsumer = consumer;
        }
        return consumer;
    }

    void createTopicAsClusterManager() throws Exception {

        Properties bobAdminProps = buildAdminClientConfig(tokens.get(BOB));
        AdminClient admin = AdminClient.create(bobAdminProps);

        //
        // Create x_* topic
        //
        admin.createTopics(Arrays.asList(new NewTopic[]{
            new NewTopic(TOPIC_X, 1, (short) 1)
        })).all().get();
    }

    private void testClusterManager() throws Exception {

        Properties bobAdminProps = buildProducerConfig(tokens.get(BOB));
        Producer<String, String> producer = new KafkaProducer<>(bobAdminProps);

        Properties consumerProps = buildConsumerConfig(tokens.get(BOB));
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

        //
        // bob should succeed producing to x_* topic
        //
        produce(producer, TOPIC_X);

        //
        // bob should succeed producing to a_* topic
        //
        produce(producer, TOPIC_A);

        //
        // bob should succeed producing to b_* topic
        //
        produce(producer, TOPIC_B);

        //
        // bob should succeed producing to non-existing topic
        //
        produce(producer, "non-existing-topic");

        //
        // bob should succeed consuming from x_* topic
        //
        consume(consumer, TOPIC_X);

        //
        // bob should succeed consuming from a_* topic
        //
        consume(consumer, TOPIC_A);

        //
        // bob should succeed consuming from b_* topic
        //
        consume(consumer, TOPIC_B);

        //
        // bob should succeed consuming from "non-existing-topic" - which now exists
        //
        consume(consumer, "non-existing-topic");
    }


    private void testTeamAClientPart1() throws Exception {

        Producer<String, String> teamAProducer = getProducer(TEAM_A_CLIENT);

        //
        // team-a-client should fail to produce to b_* topic
        //
        produceFail(teamAProducer, TOPIC_B);

        // Re-init producer because message to topicB is stuck in the queue, and any subsequent message to another queue
        // won't be handled until first message makes it through.
        teamAProducer = newProducer(TEAM_A_CLIENT);

        //
        // team-a-client should succeed producing to a_* topic
        //
        produce(teamAProducer, TOPIC_A);

        //
        // team-a-client should also fail producing to non-existing x_* topic (fails to create it)
        //
        produceFail(teamAProducer, TOPIC_X);

        Consumer<String, String> teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_B);

        //
        // team-a-client should fail consuming from b_* topic
        //
        consumeFail(teamAConsumer, TOPIC_B);


        // Close and re-init consumer
        teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_A);

        //
        // team-a-client should succeed consuming from a_* topic
        //
        consume(teamAConsumer, TOPIC_A);

        //
        // team-a-client should fail consuming from x_* topic - it doesn't exist
        //
        consumeFail(teamAConsumer, TOPIC_X);
    }


    private void testTeamBClientPart1() throws Exception {

        Producer<String, String> teamBProducer = getProducer(TEAM_B_CLIENT);

        //
        // team-b-client should fail to produce to a_* topic
        //
        produceFail(teamBProducer, TOPIC_A);

        // Re-init producer because message to topicA is stuck in the queue, and any subsequent message to another queue
        // won't be handled until first message makes it through.
        teamBProducer = newProducer(TEAM_B_CLIENT);

        //
        // team-b-client should succeed producing to b_* topic
        //
        produce(teamBProducer, TOPIC_B);

        //
        // team-b-client should fail to produce to x_* topic
        //
        produceFail(teamBProducer, TOPIC_X);


        Consumer<String, String> teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_A);

        //
        // team-b-client should fail consuming from a_* topic
        //
        consumeFail(teamBConsumer, TOPIC_A);

        // Close and re-init consumer
        teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_B);

        //
        // team-b-client should succeed consuming from b_* topic
        //
        consume(teamBConsumer, TOPIC_B);
    }

    private void testTeamAClientPart2() throws Exception {

        //
        // team-a-client should succeed producing to existing x_* topic
        //
        Producer<String, String> teamAProducer = newProducer(TEAM_A_CLIENT);

        produce(teamAProducer, TOPIC_X);

        //
        // team-a-client should fail reading from x_* topic
        //
        Consumer<String, String> teamAConsumer = newConsumer(TEAM_A_CLIENT, TOPIC_A);
        consumeFail(teamAConsumer, TOPIC_X);
    }


    private void testTeamBClientPart2() throws Exception {
        //
        // team-b-client should succeed consuming from x_* topic
        //
        Consumer<String, String> teamBConsumer = newConsumer(TEAM_B_CLIENT, TOPIC_B);
        consume(teamBConsumer, TOPIC_X);


        //
        // team-b-client should fail producing to x_* topic
        //
        Producer<String, String> teamBProducer = newProducer(TEAM_B_CLIENT);
        produceFail(teamBProducer, TOPIC_X);
    }


    /**
     * Use Keycloak Admin API to update Authorization Services 'decisionStrategy' on 'kafka' client to AFFIRMATIVE
     *
     * @throws IOException
     */
    static void fixBadlyImportedAuthzSettings() throws IOException {

        URI masterTokenEndpoint = URI.create("http://" + HOST + ":8080/auth/realms/master/protocol/openid-connect/token");

        String token = loginWithUsernamePassword(masterTokenEndpoint,
                "admin", "admin", "admin-cli");

        String authorization = "Bearer " + token;

        // This is quite a round-about way but here it goes

        // We first need to identify the 'id' of the 'kafka' client by fetching the clients
        JsonNode clients = HttpUtil.get(URI.create("http://" + HOST + ":8080/auth/admin/realms/kafka-authz/clients"),
                authorization, JsonNode.class);

        String id = null;

        // iterate over clients
        Iterator<JsonNode> it = clients.iterator();
        while (it.hasNext()) {
            JsonNode client = it.next();
            String clientId = client.get("clientId").asText();
            if ("kafka".equals(clientId)) {
                id = client.get("id").asText();
                break;
            }
        }

        if (id == null) {
            throw new IllegalStateException("It seems that 'kafka' client isn't configured");
        }

        URI authzUri = URI.create("http://" + HOST + ":8080/auth/admin/realms/kafka-authz/clients/" + id + "/authz/resource-server");

        // Now we fetch from this client's resource-server the current configuration
        ObjectNode authzConf = (ObjectNode) HttpUtil.get(authzUri, authorization, JsonNode.class);

        // And we update the configuration and send it back
        authzConf.put("decisionStrategy", "AFFIRMATIVE");
        HttpUtil.put(authzUri, authorization, "application/json", authzConf.toString());
    }


    static String groupFor(String topic) {
        return topic + "-group";
    }

    static HashMap<String, String> authenticateAllActors() throws IOException {

        HashMap<String, String> tokens = new HashMap<>();
        tokens.put(TEAM_A_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret", true, null, null).token());
        tokens.put(TEAM_B_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret", true, null, null).token());
        tokens.put(BOB, loginWithUsernamePassword(URI.create(TOKEN_ENDPOINT_URI),
                BOB, BOB + "-password", "kafka-cli"));
        return tokens;
    }

    static void consume(Consumer<String, String> consumer, String topic) {
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Arrays.asList(partition));

        while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }

        consumer.seekToBeginning(Arrays.asList(partition));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        Assert.assertTrue("Got message", records.count() >= 1);
    }

    static void consumeFail(Consumer<String, String> consumer, String topic) {
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(Arrays.asList(partition));

        try {
            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }

            consumer.seekToBeginning(Arrays.asList(partition));
            consumer.poll(Duration.ofSeconds(1));

            Assert.fail("Should fail with TopicAuthorizationException");
        } catch (TopicAuthorizationException e) {
        }
    }

    static void produce(Producer<String, String> producer, String topic) throws Exception {
        producer.send(new ProducerRecord<>(topic, "The Message")).get();
    }

    static void produceFail(Producer<String, String> producer, String topic) throws Exception {
        try {
            produce(producer, topic);
            Assert.fail("Should not be able to send message");
        } catch (ExecutionException e) {
            // should get authorization exception
            Assert.assertTrue("Should fail with TopicAuthorizationException", e.getCause() instanceof TopicAuthorizationException);
        }
    }

    static Properties buildProducerConfig(String accessToken) {
        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                " oauth.access.token=\"" + accessToken + "\";");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        return p;
    }

    static Properties buildAdminClientConfig(String accessToken) {
        return buildProducerConfig(accessToken);
    }

    static Properties buildConsumerConfig(String accessToken) {
        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                " oauth.access.token=\"" + accessToken + "\";");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        return p;
    }


    static String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

        StringBuilder body = new StringBuilder("grant_type=password&username=" + urlencode(username) +
                "&password=" + urlencode(password) + "&client_id=" + urlencode(clientId));

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                null,
                "application/x-www-form-urlencoded",
                body.toString(),
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }

    void cleanup() throws Exception {
        Properties bobAdminProps = buildAdminClientConfig(tokens.get(BOB));
        AdminClient admin = AdminClient.create(bobAdminProps);

        admin.deleteTopics(Arrays.asList(TOPIC_A, TOPIC_B, TOPIC_X, "non-existing-topic"));
        admin.deleteConsumerGroups(Arrays.asList(groupFor(TOPIC_A), groupFor(TOPIC_B), groupFor(TOPIC_X), groupFor("non-existing-topic")));
    }
}
