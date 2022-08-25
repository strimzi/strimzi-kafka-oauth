/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class TestContainersWatcher extends TestWatcher {

    public void starting(Description description) {
        System.out.println("\nUsing Kafka Image: " + System.getProperty("KAFKA_DOCKER_IMAGE") + "\n");
    }
}
