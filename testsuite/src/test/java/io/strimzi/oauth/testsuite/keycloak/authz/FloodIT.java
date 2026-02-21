/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.ConcurrentKafkaClientsRunner;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.loginWithClientSecret;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Concurrent authorization flood test
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
public class FloodIT extends AbstractAuthzIT {

    private static final Logger log = LoggerFactory.getLogger(FloodIT.class);

    OAuthEnvironmentExtension env;

    @Override
    protected String kafkaBootstrap() {
        return env.getBootstrapServers();
    }

    @Override
    protected Properties buildProducerConfigForAccount(String name) {
        return buildProducerConfigOAuthBearer(kafkaBootstrap(), buildAuthConfigForOAuthBearer(name));
    }

    @Override
    protected Properties buildConsumerConfigForAccount(String name) {
        return KafkaClientsConfig.buildConsumerConfigOAuthBearer(kafkaBootstrap(), buildAuthConfigForOAuthBearer(name));
    }


    /**
     * This test uses the Kafka listener configured with both OAUTHBEARER and PLAIN.
     * <p>
     * It connects concurrently with multiple producers with different client IDs using the PLAIN mechanism, testing the OAuth over PLAIN functionality.
     * With KeycloakRBACAuthorizer configured, any mixup of the credentials between different clients will be caught as
     * AuthorizationException would be thrown trying to write to the topic if the user context was mismatched.
     */
    @Test
    @DisplayName("Test concurrent client credentials with flood")
    @Tag(TestTags.AUTHORIZATION)
    @Tag(TestTags.CONCURRENT)
    @Tag(TestTags.FLOOD)
    public void clientCredentialsWithFloodTest() throws Exception {

        String producerPrefix = "kafka-producer-client-";
        String consumerPrefix = "kafka-consumer-client-";

        // 10 parallel producers and consumers
        final int clientCount = 10;

        HashMap<String, String> tokens = new HashMap<>();
        for (int i = 1; i <= clientCount; i++) {
            obtainAndStoreToken(producerPrefix, tokens, i);
            obtainAndStoreToken(consumerPrefix, tokens, i);
        }
        this.tokens = tokens;

        // Try write to the mismatched topic - we should get AuthorizationException
        try {
            sendSingleMessage("kafka-producer-client-1", "messages-2");

            fail("Sending to 'messages-2' using 'kafka-producer-client-1' should fail with AuthorizationException");
        } catch (InterruptedException e) {
            throw new InterruptedIOException("Interrupted");
        } catch (ExecutionException e) {
            assertInstanceOf(AuthorizationException.class, e.getCause(), "Exception type should be AuthorizationException");
        }

        ConcurrentKafkaClientsRunner runner = new ConcurrentKafkaClientsRunner();

        // Do 5 iterations - each time hitting the broker with 10 parallel requests
        for (int run = 0; run < 5; run++) {
            log.info("*** Run {}/5", run + 1);
            for (int i = 1; i <= clientCount; i++) {
                String topic = "messages-" + i;
                addProducerTask(runner, producerPrefix + i, topic);
                addConsumerTask(runner, consumerPrefix + i, topic, groupForConsumer(i));
            }

            runner.executeAll(60);
            runner.clear();
        }

        // Now try the same with a single topic
        for (int run = 0; run < 5; run++) {

            for (int i = 1; i <= clientCount; i++) {
                String topic = "messages-1";
                addProducerTask(runner, producerPrefix + "1", topic);
                addConsumerTask(runner, consumerPrefix + "1", topic, groupForConsumer(1));
            }

            runner.executeAll(60);
            runner.clear();
        }
    }

    private void addProducerTask(ConcurrentKafkaClientsRunner runner, String clientId, String topic) {
        Properties props = buildProducerConfigOAuthBearer(kafkaBootstrap(), buildAuthConfigForOAuthBearer(clientId));
        runner.addTask(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                producer.send(new ProducerRecord<>(topic, "Message 0"))
                    .get();
                log.debug("[{}] Produced message to '{}'", clientId, topic);
            }
            return null;
        });
    }

    private void addConsumerTask(ConcurrentKafkaClientsRunner runner, String clientId, String topic, String group) {
        Properties props = KafkaClientsConfig.buildConsumerConfigOAuthBearer(kafkaBootstrap(), buildAuthConfigForOAuthBearer(clientId));
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, group);
        runner.addTask(() -> {
            try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
                // This loop ensures some time for topic to be autocreated by producer which has the permissions to create the topic
                // Whereas the consumer does not have a permission to create a topic.
                for (int triesLeft = 300; triesLeft > 0; triesLeft--) {
                    try {
                        consume(consumer, topic);
                        log.debug("[{}] Consumed message from '{}'", clientId, topic);
                        break;
                    } catch (Throwable t) {
                        if (triesLeft <= 1) {
                            throw t;
                        }
                        Thread.sleep(100);
                    }
                }
            }
            return null;
        });
    }

    private void sendSingleMessage(String clientId, String topic) throws ExecutionException, InterruptedException {
        Properties props = buildProducerConfigOAuthBearer(kafkaBootstrap(), buildAuthConfigForOAuthBearer(clientId));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, "Message 0"))
                .get();
        }
    }

    private String groupForConsumer(int index) {
        return "g" + (index < 10 ? index : 0);
    }

    private void obtainAndStoreToken(String producerPrefix, HashMap<String, String> tokens, int i) throws IOException {
        String clientId = producerPrefix + i;
        String secret = clientId + "-secret";

        tokens.put(clientId, loginWithClientSecret(URI.create(env.getTokenEndpointUri()), null, null,
            clientId, secret, true, null, null, true).token());
    }
}
