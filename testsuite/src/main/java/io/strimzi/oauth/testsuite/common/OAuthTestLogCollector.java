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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * JUnit Jupiter extension that collects and dumps container logs when a test fails.
 */
public class OAuthTestLogCollector implements TestWatcher, BeforeTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(OAuthTestLogCollector.class);

    private final Supplier<List<GenericContainer<?>>> containersSupplier;

    private static final String SEPARATOR = String.join("", Collections.nCopies(76, "#"));

    /**
     * Create a new log collector with a supplier that returns the list of containers.
     *
     * @param containersSupplier A supplier that returns the current list of containers
     */
    public OAuthTestLogCollector(Supplier<List<GenericContainer<?>>> containersSupplier) {
        this.containersSupplier = containersSupplier;
    }

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

        log.error("{}.{}-FAILED", context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName());

        List<GenericContainer<?>> containers = containersSupplier.get();
        if (containers != null) {
            String dirPath = "target/test-logs/" + className + "/" + sanitize(displayName);
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

    private void writeContainerLog(GenericContainer<?> container, File dir) {
        try {
            String name = Optional.ofNullable(container.getNetworkAliases())
                    .filter(aliases -> !aliases.isEmpty())
                    .map(aliases -> aliases.get(0))
                    .orElseGet(() -> container.getDockerImageName());
            String sanitizedName = sanitize(name);
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
