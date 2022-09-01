/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class TestContainersLogCollector extends TestWatcher {

    private final TestContainersWatcher environment;

    public TestContainersLogCollector(TestContainersWatcher environment) {
        this.environment = environment;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        environment.collectLogsOnExit();
    }
}
