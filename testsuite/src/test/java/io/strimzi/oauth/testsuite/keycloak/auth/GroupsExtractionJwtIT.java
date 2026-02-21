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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;

@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(realm = "kafka-authz",
    oauthProperties = {
        "oauth.config.id=CUSTOM",
        "oauth.check.issuer=false",
        "oauth.check.access.token.type=false",
        "oauth.custom.claim.check=@.typ == 'Bearer' && @.iss == 'http://keycloak:8080/realms/kafka-authz' && 'kafka' in @.aud && 'kafka-user' in @.resource_access.kafka.roles",
        "oauth.groups.claim=$.realm_access.roles",
        "oauth.fallback.username.claim=client_id",
        "oauth.fallback.username.prefix=service-account-",
        "unsecuredLoginStringClaim_sub=admin"
    }))
public class GroupsExtractionJwtIT {

    private static final Logger log = LoggerFactory.getLogger(GroupsExtractionJwtIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @DisplayName("Groups extraction with JWT validation")
    @Tag(TestTags.JWT)
    @Tag(TestTags.GROUPS)
    void groupsExtractionWithJwtTest() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getKeycloakHostPort();
        final String realm = "kafka-authz";

        final String tokenEndpointUri = "http://" + hostPort + "/realms/" + realm + "/protocol/openid-connect/token";

        // logging in as 'team-b-client' should succeed - iss check, clientId check, aud check, resource_access check should all pass

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "team-b-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "team-b-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        final String topic = "KeycloakAuthenticationTest-customClaimCheckWithJwtTest";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "The Message")).get();
            log.debug("Produced The Message");
        }

        // get kafka log and make sure groups were extracted during authentication
        String logFilter = "principalName: service-account-team-b-client, groups: [offline_access, Dev Team B]";
        List<String> lines = getContainerLogsForString(env.getKafka(), logFilter);
        Assertions.assertTrue(lines.size() > 0, "Kafka log should contain: \"" + logFilter + "\"");
    }
}
