/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

import static java.util.Collections.singletonList;


public class BasicTest extends Common {

    public BasicTest(String kafkaBootstrap, boolean oauthOverPlain) {
        super(kafkaBootstrap, oauthOverPlain);
    }

    public void doTest() throws Exception {

        tokens = authenticateAllActors();

        testTeamAClientPart1();

        testTeamBClientPart1();

        createTopicAsClusterManager();

        testTeamAClientPart2();

        testTeamBClientPart2();

        testClusterManager();

        cleanup();
    }

    void createTopicAsClusterManager() throws Exception {

        Properties bobAdminProps = buildAdminConfigForAccount(BOB);
        AdminClient admin = AdminClient.create(bobAdminProps);

        //
        // Create x_* topic
        //
        admin.createTopics(singletonList(new NewTopic(TOPIC_X, 1, (short) 1))).all().get();
    }

    void testClusterManager() throws Exception {

        Properties bobAdminProps = buildProducerConfigForAccount(BOB);
        Producer<String, String> producer = new KafkaProducer<>(bobAdminProps);

        Properties consumerProps = buildConsumerConfigForAccount(BOB);
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

    void testTeamAClientPart1() throws Exception {

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

    void testTeamBClientPart1() throws Exception {

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

    void testTeamAClientPart2() throws Exception {

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

    void testTeamBClientPart2() throws Exception {
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
}
