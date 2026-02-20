/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.strimzi.oauth.testsuite.environment.TestContainerFactory.CONTAINER_LABEL_KEY;

/**
 * JUnit Jupiter extension that collects and dumps container logs when a test fails.
 *
 * <p>This extension discovers containers automatically by:
 * <ol>
 *   <li>Checking {@code ExtensionContext.Store} for a {@code ContainerSource} stored by {@code OAuthEnvironmentExtension}</li>
 *   <li>Scanning test instance fields for {@code ContainerSource} implementations</li>
 *   <li>Checking if the test instance itself implements {@code ContainerSource}</li>
 * </ol>
 */
public class ContainerLogCollectorExtension implements TestWatcher, BeforeTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogCollectorExtension.class);

    private static final String SEPARATOR = String.join("", Collections.nCopies(76, "#"));

    /**
     * Key used to store a {@code ContainerSource} in the ExtensionContext store.
     * Used by OAuthEnvironmentExtension to make its containers available for log collection.
     */
    public static final String CONTAINER_SOURCE_KEY = "containerSource";

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
            String dirPath = "target/container-logs/" + className + "/" + sanitize(displayName);
            File dir = new File(dirPath);
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
     * Discover containers from various sources in priority order.
     */
    private List<GenericContainer<?>> discoverContainers(ExtensionContext context) {
        // 1. Check ExtensionContext store for ContainerSource stored by OAuthEnvironmentExtension
        ContainerSource stored = context.getStore(ExtensionContext.Namespace.GLOBAL)
                .get(CONTAINER_SOURCE_KEY, ContainerSource.class);
        if (stored != null) {
            List<GenericContainer<?>> containers = stored.getContainers();
            if (containers != null && !containers.isEmpty()) {
                return containers;
            }
        }

        // 2. Check if the test instance itself implements ContainerSource
        Object testInstance = context.getTestInstance().orElse(null);
        if (testInstance instanceof ContainerSource) {
            List<GenericContainer<?>> containers = ((ContainerSource) testInstance).getContainers();
            if (containers != null && !containers.isEmpty()) {
                return containers;
            }
        }

        // 3. Scan test instance fields for ContainerSource implementations
        if (testInstance != null) {
            List<GenericContainer<?>> containers = scanFieldsForContainers(testInstance);
            if (!containers.isEmpty()) {
                return containers;
            }
        }

        return Collections.emptyList();
    }

    /**
     * Scan the test instance fields for ContainerSource implementations and GenericContainer fields.
     */
    private List<GenericContainer<?>> scanFieldsForContainers(Object testInstance) {
        List<GenericContainer<?>> result = new ArrayList<>();
        Class<?> clazz = testInstance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(testInstance);
                    if (value instanceof ContainerSource) {
                        List<GenericContainer<?>> containers = ((ContainerSource) value).getContainers();
                        if (containers != null) {
                            result.addAll(containers);
                        }
                    } else if (value instanceof GenericContainer) {
                        result.add((GenericContainer<?>) value);
                    }
                } catch (IllegalAccessException e) {
                    // skip inaccessible fields
                }
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    private void writeContainerLog(GenericContainer<?> container, File dir) {
        try {
            String sanitizedName = sanitize(container.getLabels().get(CONTAINER_LABEL_KEY));
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

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
