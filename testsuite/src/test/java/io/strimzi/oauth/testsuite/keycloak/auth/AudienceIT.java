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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.expectSaslAuthFailure;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;

@OAuthEnvironment(authServer = AuthServer.KEYCLOAK)
public class AudienceIT {

    private static final Logger log = LoggerFactory.getLogger(AudienceIT.class);

    OAuthEnvironmentExtension env;

    @KafkaConfig(
        realm = "kafka-authz",
        oauthProperties = {
            "oauth.config.id=AUDIENCEINTROSPECT",
            "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.check.audience=true",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    @Test
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.AUDIENCE)
    void testClientCredentialsWithIntrospectionAudience() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithIntrospectionAudienceTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, "message");

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        expectSaslAuthFailure(producerProps, topic, msg ->
            Assertions.assertTrue(msg.contains("Invalid audience"), "'Invalid audience' error message"));
    }

    @KafkaConfig(
        realm = "kafka-authz",
        oauthProperties = {
            "oauth.config.id=AUDIENCE",
            "oauth.check.audience=true",
            "oauth.client.id=kafka",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    @Test
    @Tag(TestTags.JWT)
    @Tag(TestTags.AUDIENCE)
    void testClientCredentialsWithJwtAudience() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        String topic = "KeycloakAuthenticationTest-clientCredentialsWithJwtAudienceTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, "message");

        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-a-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-a-client-secret");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        expectSaslAuthFailure(producerProps, topic, msg ->
            Assertions.assertTrue(msg.contains("audience not available"), "'audience not available' error mesage"));
    }
}
