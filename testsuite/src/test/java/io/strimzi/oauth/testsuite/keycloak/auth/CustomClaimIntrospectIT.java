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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.loginWithUsernamePassword;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.expectSaslAuthFailure;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;

@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "kafka-authz",
        oauthProperties = {
            "oauth.config.id=CUSTOMINTROSPECT",
            "oauth.introspection.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.check.issuer=false",
            "oauth.check.access.token.type=false",
            "oauth.custom.claim.check=@.typ == 'Bearer' && @.iss == 'http://keycloak:8080/realms/kafka-authz' && 'kafka' in @.aud && 'kafka-user' in @.resource_access.kafka.roles",
            "oauth.groups.claim=$['resource_access']['kafka']['roles']",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class CustomClaimIntrospectIT {

    private static final Logger log = LoggerFactory.getLogger(CustomClaimIntrospectIT.class);

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.CUSTOM_CHECK)
    void customClaimCheckWithIntrospectionTest() throws Exception {
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
        produceMessage(producerProps, topic, "The Message");

        // logging in as 'bob' should fail - clientId check, aud check and resource_access check would all fail
        String token = loginWithUsernamePassword(URI.create(tokenEndpointUri), "bob", "bob-password", "kafka-cli");

        oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);
        oauthConfig.put(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        expectSaslAuthFailure(producerProps, topic, msg ->
            Assertions.assertTrue(msg.contains("Custom claim check failed"), "custom claim check failed"));
    }

    @Test
    @Tag(TestTags.INTROSPECTION)
    @Tag(TestTags.GROUPS)
    void groupsExtractionWithIntrospectionTest() throws Exception {
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
        produceMessage(producerProps, topic, "The Message");

        // get kafka log and make sure groups were extracted during authentication
        String logFilter = "principalName: service-account-team-b-client, groups: [kafka-user]";
        List<String> lines = getContainerLogsForString(env.getKafka(), logFilter);
        Assertions.assertTrue(!lines.isEmpty(), "Kafka log should contain: \"" + logFilter + "\"");
    }
}
