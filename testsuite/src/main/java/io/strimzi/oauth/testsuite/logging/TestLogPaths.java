/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.logging;

import java.io.File;

/**
 * Centralizes all test log directory naming conventions.
 *
 * <p>All logs are placed under {@code target/test-logs/} organized by package/class path:
 * <ul>
 *   <li>{@code target/test-logs/.../AuthBasicIT/} — per-class root</li>
 *   <li>{@code target/test-logs/.../AuthBasicIT/containers/} — always-collected container logs and startup failure logs</li>
 *   <li>{@code target/test-logs/.../AuthBasicIT/failures/{methodName}/} — point-in-time snapshots on test failure</li>
 * </ul>
 */
public final class TestLogPaths {

    private static final String BASE_DIR = "target/test-logs";

    /**
     * Path into root target where current test log will be stored.
     */
    public static final String CURRENT_TEST_LOG_PATH = "target/test.log";

    private TestLogPaths() {
    }

    /**
     * Root directory for a test class's logs.
     * Converts the fully qualified class name to a directory path
     * (e.g. {@code io.strimzi.oauth.testsuite.keycloak.auth.AuthBasicIT}
     * becomes {@code target/test-logs/.../keycloak/auth/AuthBasicIT/}).
     *
     * @param className fully qualified test class name
     * @return the class log directory
     */
    public static File classDir(String className) {
        return new File(BASE_DIR, className.replace('.', File.separatorChar));
    }

    /**
     * Directory for container logs (always-collected and startup failure logs).
     *
     * @param className fully qualified test class name
     * @return {@code target/test-logs/{className}/containers/}
     */
    public static File containersDir(String className) {
        return new File(classDir(className), "containers");
    }

    /**
     * Directory for point-in-time container log snapshots captured on test failure.
     *
     * @param className  fully qualified test class name
     * @param methodName test method name
     * @return {@code target/test-logs/{className}/failures/{methodName}/}
     */
    public static File failureDir(String className, String methodName) {
        return new File(classDir(className), "failures/" + sanitize(methodName));
    }

    /**
     * Sanitize a name for use as a file/directory name by replacing
     * non-alphanumeric characters (except dot, dash, underscore) with underscores.
     *
     * @param name the name to sanitize
     * @return sanitized name safe for filesystem use
     */
    public static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
