/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.testsuite.oauth.common.ContainerLogLineReader;
import org.junit.Assert;

import java.util.List;
import java.util.stream.Collectors;

@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
public class SingletonTest {

    private final String kafkaContainer;

    public SingletonTest(String kafkaContainer) {
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

        Assert.assertEquals("Configured KeycloakAuthorizer", keycloakAuthorizersCount, keycloakAuthorizerLines.size());
        Assert.assertEquals("Configured KeycloakRBACAuthorizer", 1, keycloakRBACAuthorizerLines.size());
    }
}
