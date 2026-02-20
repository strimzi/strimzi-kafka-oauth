/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that enables automatic container log collection on test failure.
 *
 * <p>When applied to a test class, this annotation registers a JUnit extension that:
 * <ul>
 *   <li>Logs test lifecycle events (started, succeeded, failed, aborted, disabled)</li>
 *   <li>On test failure, saves all container logs to {@code target/container-logs/{class}/{method}/}</li>
 * </ul>
 *
 * <p>Containers are discovered automatically by:
 * <ol>
 *   <li>Checking {@code ExtensionContext.Store} for a {@code ContainerSource} stored by {@code OAuthEnvironmentExtension}</li>
 *   <li>Scanning test instance fields for {@code ContainerSource} implementations</li>
 *   <li>Scanning test instance for the {@code ContainerSource} interface on the class itself</li>
 * </ol>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ContainerLogCollectorExtension.class)
public @interface TestLogCollector {
}
