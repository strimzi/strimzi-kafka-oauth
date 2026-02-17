/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * JUnit Jupiter extension that collects and dumps container logs when a test fails.
 */
public class OAuthTestLogCollector implements TestWatcher {

    private final Supplier<List<GenericContainer<?>>> containersSupplier;

    /**
     * Create a new log collector with a supplier that returns the list of containers.
     *
     * @param containersSupplier A supplier that returns the current list of containers
     */
    public OAuthTestLogCollector(Supplier<List<GenericContainer<?>>> containersSupplier) {
        this.containersSupplier = containersSupplier;
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        System.out.println("\n\n===== Test failed: " + context.getDisplayName() + " =====\n");
        List<GenericContainer<?>> containers = containersSupplier.get();
        if (containers != null) {
            for (GenericContainer<?> container : containers) {
                dumpContainerLog(container);
            }
        }
    }

    private void dumpContainerLog(GenericContainer<?> container) {
        try {
            String name = Optional.ofNullable(container.getNetworkAliases())
                    .filter(aliases -> !aliases.isEmpty())
                    .map(aliases -> aliases.get(0))
                    .orElseGet(() -> container.getContainerName());
            System.out.println("\n\n'" + name + "' log:\n\n" + container.getLogs() + "\n");
        } catch (Exception e) {
            System.out.println("Failed to get container logs: " + e.getMessage());
        }
    }
}
