/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.testsuite.oauth.common.ContainerLogLineReader;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public class SingletonTest {

    private final GenericContainer<?> kafkaContainer;

    public SingletonTest(GenericContainer<?> kafkaContainer) {
        this.kafkaContainer = kafkaContainer;
    }

    /**
     * Ensure that multiple instantiated KeycloakAuthorizers share a single instance of KeycloakRBACAuthorizer");
     *
     * @throws Exception If any error occurs
     */
    public void doSingletonTest(int keycloakAuthorizersCount) throws Exception {

        ContainerLogLineReader logReader = new ContainerLogLineReader(kafkaContainer);

        List<String> lines = logReader.readNext();
        List<String> keycloakAuthorizerLines = lines.stream().filter(line -> line.contains("Configured KeycloakAuthorizer@")).collect(Collectors.toList());
        List<String> keycloakRBACAuthorizerLines = lines.stream().filter(line -> line.contains("Configured KeycloakRBACAuthorizer@")).collect(Collectors.toList());

        assertEquals(keycloakAuthorizersCount, keycloakAuthorizerLines.size(), "Configured KeycloakAuthorizer");
        assertEquals(1, keycloakRBACAuthorizerLines.size(), "Configured KeycloakRBACAuthorizer");
    }
}
