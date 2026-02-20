/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.KafkaPreset;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
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
    kafka = @KafkaConfig(
        preset = KafkaPreset.KEYCLOAK_AUTHZ,
        setupAcls = true
    )
)
@DisplayName("OAuth over PLAIN Authorization Tests")
public class AuthzOAuthOverPlainIT extends AbstractAuthzIT {

    OAuthEnvironmentExtension env;

    @Override
    protected String kafkaBootstrap() {
        return "localhost:9094";
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
