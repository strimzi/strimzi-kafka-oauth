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

        String topicA = "a_messages";
        String topicB = "b_messages";
        String topicX = "x_messages";

        HashMap<String, String> tokens = authenticateAllActors();

        Properties teamAProducerProps = buildProducerConfig(tokens.get(TEAM_A_CLIENT));

        Producer<String, String> teamAProducer = new KafkaProducer<>(teamAProducerProps);

        try {
            teamAProducer.send(new ProducerRecord<>(topicB, "The Message")).get();
            Assert.fail("Should not be able to send message");
        } catch (ExecutionException e) {
            // should get authorization exception
            Assert.assertTrue("Should fail with TopicAuthorizationException", e.getCause() instanceof TopicAuthorizationException);
        }

        // Re-init producer because message to topicB is stuck in the queue, and any subsequent message to another queue
        // won't be handled until first message makes it through.
        teamAProducer.close();
        teamAProducer = new KafkaProducer<>(teamAProducerProps);

        teamAProducer.send(new ProducerRecord<>(topicA, "The Message")).get();
        System.out.println("Produced The Message");

        Properties teamAConsumerProps = buildConsumerConfig(tokens.get(TEAM_A_CLIENT));

        Consumer<String, String> teamAConsumer = new KafkaConsumer<>(teamAConsumerProps);

        TopicPartition partition = new TopicPartition(topicB, 0);
        teamAConsumer.assign(Arrays.asList(partition));

        // try read b_topic
        try {
            while (teamAConsumer.partitionsFor(topicB, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            Assert.fail("Should fail with TopicAuthorizationException");
        } catch (TopicAuthorizationException e) {
        }

        // Close and re-init consumer
        teamAConsumer.close();
        teamAConsumer = new KafkaConsumer<>(teamAConsumerProps);

        // try read a_topic
        partition = new TopicPartition(topicA, 0);
        teamAConsumer.assign(Arrays.asList(partition));

        while (teamAConsumer.partitionsFor(topicA, Duration.ofSeconds(1)).size() == 0) {
            System.out.println("No assignment yet for consumer");
        }

        // try read a_topic
        teamAConsumer.seekToBeginning(Arrays.asList(partition));


        ConsumerRecords<String, String> records = teamAConsumer.poll(Duration.ofSeconds(1));

        Assert.assertEquals("Got message", 1, records.count());
        Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
    }


    private HashMap<String, String> authenticateAllActors() throws IOException {

        HashMap<String, String> tokens = new HashMap<>();
        tokens.put(TEAM_A_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret", true).token());
        tokens.put(TEAM_B_CLIENT, loginWithClientSecret(URI.create(TOKEN_ENDPOINT_URI), null, null,
                TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret", true).token());
        tokens.put(BOB, loginWithUsernamePassword(URI.create(TOKEN_ENDPOINT_URI),
                BOB, BOB + "-password", "kafka-cli"));
        return tokens;
    }


    private void fixBadlyImportedAuthzSettings() throws IOException {
        // Use Keycloak Admin API to update decisionStrategy on 'kafka' client to AFFIRMATIVE

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

    private static Properties buildProducerConfig(String accessToken) {
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


    private static Properties buildConsumerConfig(String accessToken) {
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


    private String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

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
}
