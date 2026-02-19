/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.clients.KafkaClientsConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import java.util.Properties;

/**
 * OAuth over PLAIN authorization tests - runs the same authorization scenarios as BasicIT
 * but with OAuth over PLAIN configuration
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OAuth over PLAIN Authorization Tests")
public class AuthzOAuthOverPlainIT extends AbstractAuthzIT {

    private GenericContainer<?> kafkaContainer;
    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

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
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();

        authenticateAllActors(environment.getTokenEndpointUri());
    }

    @AfterAll
    void tearDown() {
        cleanup();
        if (environment != null) {
            environment.stop();
        }
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
        doTestUserWithNoPermissions(kafkaContainer);
    }
}
