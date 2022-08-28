/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TestContainersWatcher implements TestRule {

    private DockerComposeContainer<?> environment;

    public TestContainersWatcher(File composeFile) {
        this.environment = new DockerComposeContainer<>(composeFile)
                .withLocalCompose(true)
                .withEnv("KAFKA_DOCKER_IMAGE", System.getProperty("KAFKA_DOCKER_IMAGE"));
    }

    public TestContainersWatcher withServices(@NonNull String... services) {
        environment.withServices(services);
        return this;
    }

    public TestContainersWatcher waitingFor(String serviceName, @NonNull WaitStrategy waitStrategy) {
        environment.waitingFor(serviceName, waitStrategy);
        return this;
    }

    public Optional<ContainerState> getContainerByServiceName(String serviceName) {
        return environment.getContainerByServiceName(serviceName);
    }

    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                List<Throwable> errors = new ArrayList<Throwable>();

                try {
                    starting(description);
                    environment.start();
                    base.evaluate();
                } catch (Throwable e) {
                    errors.add(e);
                    failed(e, description);
                } finally {
                    environment.stop();
                }

                MultipleFailureException.assertEmpty(errors);
            }
        };
    }

    public void starting(Description description) {
        System.out.println("\nUsing Kafka Image: " + System.getProperty("KAFKA_DOCKER_IMAGE") + "\n");
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
