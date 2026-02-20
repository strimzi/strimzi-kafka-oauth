/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

import org.testcontainers.containers.GenericContainer;

import java.util.List;

/**
 * Interface for objects that provide access to a list of Docker containers,
 * typically used for log collection on test failure.
 */
public interface ContainerSource {

    /**
     * Get the list of containers managed by this source.
     *
     * @return A list of containers, or an empty list if none are available
     */
    List<GenericContainer<?>> getContainers();
}
