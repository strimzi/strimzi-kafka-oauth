/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.logging;

import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.strimzi.oauth.testsuite.environment.TestContainerFactory.CONTAINER_LABEL_KEY;

/**
 * JUnit Jupiter extension that collects and dumps container logs when a test fails.
 *
 * <p>Containers are discovered via the {@code OAuthEnvironmentExtension} stored
 * in the {@code ExtensionContext} by the {@code @OAuthEnvironment} annotation.
 */
public class TestLogCollectorExtension implements TestWatcher, BeforeTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(TestLogCollectorExtension.class);

    private static final String SEPARATOR = String.join("", Collections.nCopies(76, "#"));

    /**
     * Key used to store the {@code OAuthEnvironmentExtension} in the ExtensionContext store.
     */
    public static final String ENVIRONMENT_KEY = "oauthEnvironment";

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        log.info(SEPARATOR);
        log.info("{}.{}-STARTED", context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        log.info(SEPARATOR);
        log.info("{}.{}-SUCCEEDED", context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String className = context.getRequiredTestClass().getName();
        String displayName = context.getRequiredTestMethod().getName();

        log.error("{}.{}-FAILED", className, displayName);

        List<GenericContainer<?>> containers = discoverContainers(context);
        if (!containers.isEmpty()) {
            File dir = TestLogPaths.failureDir(className, displayName);
            dir.mkdirs();

            for (GenericContainer<?> container : containers) {
                writeContainerLog(container, dir);
            }
            log.info("Container logs saved to: {}", dir.getAbsolutePath());
        }
        log.info(SEPARATOR);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        log.warn("{}.{}-ABORTED", context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName());
        log.info(SEPARATOR);
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        log.info("{}.{}-DISABLED", context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName());
        log.info(SEPARATOR);
    }

    /**
     * Get containers from the OAuthEnvironmentExtension stored in the context.
     */
    private List<GenericContainer<?>> discoverContainers(ExtensionContext context) {
        OAuthEnvironmentExtension env = context.getStore(ExtensionContext.Namespace.GLOBAL)
                .get(ENVIRONMENT_KEY, OAuthEnvironmentExtension.class);
        if (env != null) {
            List<GenericContainer<?>> containers = env.getContainers();
            if (containers != null && !containers.isEmpty()) {
                return containers;
            }
        }
        return Collections.emptyList();
    }

    private void writeContainerLog(GenericContainer<?> container, File dir) {
        try {
            String sanitizedName = TestLogPaths.sanitize(container.getLabels().get(CONTAINER_LABEL_KEY));
            File logFile = new File(dir, sanitizedName + ".log");
            try (FileWriter writer = new FileWriter(logFile)) {
                writer.write(container.getLogs());
            }
        } catch (IOException e) {
            log.error("Failed to write container log file: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to get container logs: {}", e.getMessage(), e);
        }
    }
}
