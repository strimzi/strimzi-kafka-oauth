/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.authz;

import io.strimzi.oauth.testsuite.environment.KeycloakAuthzKRaftTestEnvironment;
import io.strimzi.oauth.testsuite.common.ContainerLogLineReader;
import io.strimzi.oauth.testsuite.common.OAuthTestLogCollector;
import io.strimzi.oauth.testsuite.common.TestTags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for KeycloakAuthorizer singleton behavior
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SingletonIT {

    private KeycloakAuthzKRaftTestEnvironment environment;
    private GenericContainer<?> kafkaContainer;

    @RegisterExtension
    OAuthTestLogCollector logCollector = new OAuthTestLogCollector(() ->
            environment != null ? environment.getContainers() : null);

    @BeforeAll
    void setUp() {
        environment = new KeycloakAuthzKRaftTestEnvironment();
        environment.start();
        kafkaContainer = environment.getKafka();
    }

    @AfterAll
    void tearDown() {
        if (environment != null) {
            environment.stop();
        }
    }

    /**
     * Ensure that multiple instantiated KeycloakAuthorizers share a single instance of KeycloakRBACAuthorizer
     */
    @Test
    @DisplayName("Test KeycloakAuthorizer singleton behavior")
    @Tag(TestTags.SINGLETON)
    @Tag(TestTags.AUTHORIZATION)
    public void testKeycloakAuthorizerSingleton() throws Exception {
        // In KRaft mode, there are 3 KeycloakAuthorizer instances
        int keycloakAuthorizersCount = 2;

        ContainerLogLineReader logReader = new ContainerLogLineReader(kafkaContainer);

        List<String> lines = logReader.readNext();
        List<String> keycloakAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakAuthorizer@"))
                .collect(Collectors.toList());
        List<String> keycloakRBACAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakRBACAuthorizer@"))
                .collect(Collectors.toList());

        assertEquals(keycloakAuthorizersCount, keycloakAuthorizerLines.size(), "Configured KeycloakAuthorizer");
        assertEquals(1, keycloakRBACAuthorizerLines.size(), "Configured KeycloakRBACAuthorizer");
    }
}
