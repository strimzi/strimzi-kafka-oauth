/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

/**
 * Basic authorization tests for Keycloak with KRaft
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BasicIT extends Common {

    private GenericContainer<?> kafkaContainer;
    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    public BasicIT() {
        super("localhost:9092", false);
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
