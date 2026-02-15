/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import io.strimzi.testsuite.oauth.authz.kraft.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.testsuite.oauth.common.OAuthTestLogCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * OAuth over PLAIN authorization tests - extends BasicTest with OAuth over PLAIN configuration
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OAuth over PLAIN Authorization Tests")
public class OAuthOverPlainIT extends BasicIT {

    private KeycloakAuthzKRaftTestEnvironment environment;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    @Override
    void setUp() throws Exception {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
        
        // Override configuration for OAuth over PLAIN
        kafkaBootstrap = "localhost:9094";
        usePlain = true;
        
        authenticateAllActors();
    }

    @AfterAll
    @Override
    void tearDown() {
        cleanup();
        if (environment != null) {
            environment.stop();
        }
    }
}
