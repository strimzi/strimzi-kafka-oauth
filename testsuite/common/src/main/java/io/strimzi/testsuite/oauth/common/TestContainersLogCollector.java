/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.testcontainers.containers.DockerComposeContainer;

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TestContainersLogCollector extends TestWatcher {

    private DockerComposeContainer<?> environment;

    public TestContainersLogCollector(DockerComposeContainer<?> environment) {
        this.environment = environment;
    }

    protected void failed(Throwable e, Description description) {
        // Dump the logs to stdout
        environment.getContainerByServiceName("kafka_1").ifPresent(c -> System.out.println("\n\nKafka log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("mockoauth_1").ifPresent(c -> System.out.println("\n\nMockoauth log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("keycloak_1").ifPresent(c -> System.out.println("\n\nKeycloak log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("kafka-acls_1").ifPresent(c -> System.out.println("\n\nKafka ACLs log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("hydra_1").ifPresent(c -> System.out.println("\n\nHydra log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("hydra-jwt_1").ifPresent(c -> System.out.println("\n\nHydra JWT log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("hydra-import_1").ifPresent(c -> System.out.println("\n\nHydra Import log:\n" + c.getLogs() + "\n"));
        environment.getContainerByServiceName("hydra-jwt-import_1").ifPresent(c -> System.out.println("\n\nHydra JWT Import log:\n" + c.getLogs() + "\n"));
    }
}
