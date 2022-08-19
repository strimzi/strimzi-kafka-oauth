/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Test;

public class CommonTests {

    @Test
    public void doTest() throws Exception {

        // The following two tests have a race condition if order of execution is left undefined
        // For that reason they are merged into a single JUnit test

        // They both use the HttpUtil class which statically initialises the default connect timeout and read timeout
        // In order to test the static initialisation, the HttpUtilTimeoutTest() sets some system properties to
        // non-default values.
        // If the HttpUtil class is used by some other test first, then the default connect timeout and read timeout
        // will be initialised before the HttpUtilTimeoutTest has had a chance to override the defaults via system props

        // This test has to be executed first
        logStart("CommonTests :: HttpUtilTimeoutTest");
        new HttpUtilTimeoutTest().doTest();

        // The rest of the tests
        logStart("CommonTests :: UnprotectedTruststoreTest");
        new UnprotectedTruststoreTest().doTest();
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
        System.out.println();
    }
}
