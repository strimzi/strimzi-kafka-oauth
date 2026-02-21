/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import io.strimzi.test.container.AuthenticationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Properties;

/**
 * OAuth over PLAIN authorization tests - runs the same authorization scenarios as BasicIT
 * but with OAuth over PLAIN configuration
 */
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(realm = "kafka-authz",
        authenticationType = AuthenticationType.OAUTH_OVER_PLAIN,
        setupAcls = true,
        oauthProperties = {
            "oauth.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
            "oauth.jwks.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs",
            "oauth.fallback.username.claim=client_id",
            "oauth.fallback.username.prefix=service-account-",
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
        })
)
@DisplayName("OAuth over PLAIN Authorization Tests")
public class AuthzOAuthOverPlainIT extends AbstractAuthzIT {

    OAuthEnvironmentExtension env;

    @Override
    protected String kafkaBootstrap() {
        return env.getBootstrapServers();
    }

    @Override
    protected Properties buildProducerConfigForAccount(String name) {
        return buildProducerConfigPlain(kafkaBootstrap(), buildAuthConfigForPlain(name));
    }

    @Override
    protected Properties buildConsumerConfigForAccount(String name) {
        return KafkaClientsConfig.buildConsumerConfigPlain(kafkaBootstrap(), buildAuthConfigForPlain(name));
    }

    @BeforeAll
    void setUp() throws Exception {
        authenticateAllActors(env.getTokenEndpointUri());
    }

    @AfterAll
    void tearDown() {
        cleanup();
    }

    @Test
    @Tag(TestTags.AUTHORIZATION)
    public void testAuthorization() throws Exception {
        doTestTeamAClientPart1();
        doTestTeamBClientPart1();
        doCreateTopicAsClusterManager();
        doTestTeamAClientPart2();
        doTestTeamBClientPart2();
        doTestClusterManager();
        doTestUserWithNoPermissions(env.getKafka());
    }
}
