/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.testsuite.oauth.common.TestMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.auth.Common.buildConsumerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.poll;
import static io.strimzi.testsuite.oauth.common.TestMetrics.getPrometheusMetrics;
import static java.util.Collections.singletonList;

public class OKPTest {

    private static final Logger log = LoggerFactory.getLogger(OKPTest.class);

    public void doTests() throws Exception {
        clientCredentialsWithJwtOKPValidation();
    }

    void clientCredentialsWithJwtOKPValidation() throws Exception {

        System.out.println("    ====    OKPTest :: clientCredentialsWithJwtOKPValidationTest");

        final String kafkaBootstrap = "kafka:9105";
        final String authHostPort = "keycloak:8080";
        final String realm = "demo-okp";
        final String path = "/realms/" + realm + "/protocol/openid-connect/token";

        final String tokenEndpointUri = "http://" + authHostPort + path;

        // For metrics
        final String jwksPath = "/realms/" + realm + "/protocol/openid-connect/certs";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "OKPTest-clientCredentialsWithJwtOKPValidationTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(singletonList(partition));

            while (consumer.partitionsFor(topic, Duration.ofSeconds(1)).size() == 0) {
                System.out.println("No assignment yet for consumer");
            }
            consumer.seekToBeginning(singletonList(partition));

            ConsumerRecords<String, String> records = poll(consumer);

            Assert.assertEquals("Got message", 1, records.count());
            Assert.assertEquals("Is message text: 'The Message'", "The Message", records.iterator().next().value());
        }

        // Check metrics

        TestMetrics metrics = getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
        BigDecimal value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_count", "kind", "jwks", "host", authHostPort, "path", jwksPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_count for jwks > 0", value.doubleValue() > 0.0);

        value = metrics.getStartsWithValueSum("strimzi_oauth_http_requests_totaltimems", "kind", "jwks", "host", authHostPort, "path", jwksPath, "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_count", "context", "OKP", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        // There is no inter-broker connection on this listener, producer did 2 validations, and consumer also did 2 validations
        Assert.assertTrue("strimzi_oauth_validation_requests_count for jwks >= 4", value != null && value.intValue() >= 4);

        value = metrics.getStartsWithValueSum("strimzi_oauth_validation_requests_totaltimems", "context", "OKP", "kind", "jwks", "mechanism", "OAUTHBEARER", "outcome", "success");
        Assert.assertTrue("strimzi_oauth_http_requests_totaltimems for jwks > 0.0", value.doubleValue() > 0.0);
    }
}
