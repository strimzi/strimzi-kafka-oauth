/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.getKafkaLogsForString;


public class GroupsExtractionTests {

    static void doTests() throws Exception {
        groupsExtractionWithJwtTest();
        groupsExtractionWithIntrospectionTest();
    }

    private static void groupsExtractionWithJwtTest() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: groupsExtractionWithJwtTest ====");
        runTest("kafka:9098", "principalName: service-account-team-b-client, groups: [offline_access, Dev Team B]");
    }

    private static void groupsExtractionWithIntrospectionTest() throws Exception {
        System.out.println("==== KeycloakAuthenticationTest :: groupsExtractionWithIntrospectionTest ====");
        runTest("kafka:9099", "principalName: service-account-team-b-client, groups: [kafka-user]");
    }

    private static void runTest(String kafkaBootstrap, String logFilter) throws Exception {

        final String hostPort = "keycloak:8080";
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // logging in as 'team-b-client' should succeed - iss check, clientId check, aud check, resource_access check should all pass

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "KeycloakAuthenticationTest-customClaimCheckWithJwtTest";


        producer.send(new ProducerRecord<>(topic, "The Message")).get();
        System.out.println("Produced The Message");

        // get kafka log and make sure groups were extracted during authentication
        List<String> lines = getKafkaLogsForString(logFilter);
        Assert.assertTrue("Kafka log should contain: \"" + logFilter + "\"", lines.size() > 0);
    }

}
