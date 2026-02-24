/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.oauth.testsuite.utils.TestUtil;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.consume;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.consumeFail;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceFail;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressFBWarnings({"THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION", "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT"})
public abstract class AbstractAuthzIT {

    private static final Logger log = LoggerFactory.getLogger(AbstractAuthzIT.class);

    static final String TEAM_A_CLIENT = "team-a-client";
    static final String TEAM_B_CLIENT = "team-b-client";
    static final String BOB = "bob";
    static final String ZERO = "zero";

    static final String TOPIC_A = "a_messages";
    static final String TOPIC_B = "b_messages";
    static final String TOPIC_X = "x_messages";

    static final int AUTHZ_RETRIES = 10;

    OAuthEnvironmentExtension env;

    HashMap<String, String> tokens = new HashMap<>();

    String getToken(String name) {
        return tokens.get(name);
    }

    void authenticateAllActors() throws IOException {
        String tokenEndpointUri = env.getTokenEndpointUri();
        tokens.put(TEAM_A_CLIENT, loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(TEAM_B_CLIENT, loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret", true, null, null, true).token());
        tokens.put(BOB, KafkaClientsConfig.loginWithUsernamePasswordInBody(URI.create(tokenEndpointUri),
                BOB, BOB + "-password", "kafka-cli"));
        tokens.put(ZERO, KafkaClientsConfig.loginWithUsernamePasswordInBody(URI.create(tokenEndpointUri),
                ZERO, ZERO + "-password", "kafka-cli"));
    }

    static String groupFor(String topic) {
        return topic + "-group";
    }

    void cleanup(Properties adminProps) {
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.deleteTopics(Arrays.asList(TOPIC_A, TOPIC_B, TOPIC_X, "non-existing-topic"));
            admin.deleteConsumerGroups(Arrays.asList(groupFor(TOPIC_A), groupFor(TOPIC_B),
                groupFor(TOPIC_X), groupFor("non-existing-topic")));
        }
    }

    void createSharedTopicAsClusterManager(Properties adminProps) throws Exception {
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.createTopics(singletonList(new NewTopic(TOPIC_X, 1, (short) 1))).all().get();
        }
    }

    void verifyTeamACanOnlyAccessOwnTopics(Properties producerProps, Properties consumerProps) throws Exception {
        produceFail(producerProps, TOPIC_B, "The Message");
        produceMessage(producerProps, TOPIC_A, "The Message");
        produceFail(producerProps, TOPIC_X, "The Message");

        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_B));
        consumeFail(consumerProps, TOPIC_B);

        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_A));
        consume(consumerProps, TOPIC_A);
        consumeFail(consumerProps, TOPIC_X);
    }

    void verifyTeamBCanOnlyAccessOwnTopics(Properties producerProps, Properties consumerProps) throws Exception {
        produceFail(producerProps, TOPIC_A, "The Message");
        produceMessage(producerProps, TOPIC_B, "The Message");
        produceFail(producerProps, TOPIC_X, "The Message");

        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_A));
        consumeFail(consumerProps, TOPIC_A);

        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_B));
        consume(consumerProps, TOPIC_B);
    }

    void verifyTeamACanProduceToSharedTopic(Properties producerProps, Properties consumerProps) throws Exception {
        produceMessage(producerProps, TOPIC_X, "The Message");

        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_A));
        consumeFail(consumerProps, TOPIC_X);
    }

    void verifyTeamBCanConsumeFromSharedTopic(Properties consumerProps, Properties producerProps) throws Exception {
        consumerProps.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupFor(TOPIC_B));
        consume(consumerProps, TOPIC_X);

        produceFail(producerProps, TOPIC_X, "The Message");
    }

    void verifyClusterManagerCanAccessAllTopics(Properties producerProps, Properties consumerProps) throws Exception {
        produceMessage(producerProps, TOPIC_X, "The Message");
        produceMessage(producerProps, TOPIC_A, "The Message");
        produceMessage(producerProps, TOPIC_B, "The Message");
        produceMessage(producerProps, "non-existing-topic", "The Message");

        consume(consumerProps, TOPIC_X);
        consume(consumerProps, TOPIC_A);
        consume(consumerProps, TOPIC_B);
        consume(consumerProps, "non-existing-topic");
    }

    void verifyUserWithNoPermissionsIsDenied(Properties producerProps, Properties consumerProps,
            GenericContainer<?> kafkaContainer) throws Exception {
        produceFail(producerProps, TOPIC_X, "The Message");
        produceFail(producerProps, TOPIC_A, "The Message");
        produceFail(producerProps, TOPIC_B, "The Message");
        produceFail(producerProps, "non-existing-topic", "The Message");

        consumeFail(consumerProps, TOPIC_X);
        consumeFail(consumerProps, TOPIC_A);
        consumeFail(consumerProps, TOPIC_B);
        consumeFail(consumerProps, "non-existing-topic");

        List<String> lines = TestUtil.getContainerLogsForString(kafkaContainer, "Saving non-null grants for user: zero");
        assertEquals(1, lines.size(), "Saved non-null grants");

        lines = TestUtil.getContainerLogsForString(kafkaContainer, "Got grants for 'OAuthKafkaPrincipal(User:zero,");
        assertTrue(lines.size() > 0, "Grants for user are: {}");

        for (String line: lines) {
            assertTrue(line.contains(": {}"), "Grants for user are: {}");
        }
    }
}
