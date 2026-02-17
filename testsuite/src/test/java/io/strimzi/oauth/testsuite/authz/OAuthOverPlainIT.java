/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

/**
 * OAuth over PLAIN authorization tests - runs the same authorization scenarios as BasicIT
 * but with OAuth over PLAIN configuration
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OAuth over PLAIN Authorization Tests")
public class OAuthOverPlainIT extends Common {

    private GenericContainer<?> kafkaContainer;
    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    public OAuthOverPlainIT() {
        super("localhost:9094", true);
    }

    @BeforeAll
    void setUp() throws Exception {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();

        authenticateAllActors();
    }

    @AfterAll
    void tearDown() {
        cleanup();
        if (environment != null) {
            environment.stop();
        }
    }

    @Test
    @Tag("authorization")
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
