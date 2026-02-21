/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.auth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;

@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(realm = "kafka-authz",
    oauthProperties = {
        "oauth.config.id=AUDIENCEINTROSPECT",
        "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect",
        "oauth.client.id=kafka",
        "oauth.client.secret=kafka-secret",
        "oauth.check.audience=true",
        "oauth.fallback.username.claim=client_id",
        "oauth.fallback.username.prefix=service-account-",
        "unsecuredLoginStringClaim_sub=admin"
    }))
public class AudienceIntrospectIT {

    private static final Logger log = LoggerFactory.getLogger(AudienceIntrospectIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Client credentials with introspection audience validation")
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.AUDIENCE)
    void clientCredentialsWithIntrospectionAudienceTest() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

            RecordMetadata result = producer.send(new ProducerRecord<>(topic, "message"))
                .get();

            Assertions.assertTrue(result.hasOffset(), "Has offset");
        }

        log.debug("Produced The Message");

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "message2"))
                .get();
            Assertions.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assertions.assertTrue(cause instanceof AuthenticationException, "instanceOf AuthenticationException");
            Assertions.assertTrue(cause.toString()
                .contains("Invalid audience"), "'Invalid audience' error message");
        }
    }
}
