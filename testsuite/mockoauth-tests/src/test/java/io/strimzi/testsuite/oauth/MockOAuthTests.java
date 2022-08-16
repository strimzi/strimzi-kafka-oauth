/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;


import io.strimzi.testsuite.oauth.metrics.MetricsTest;
import io.strimzi.testsuite.oauth.mockoauth.JaasClientConfigTest;
import io.strimzi.testsuite.oauth.mockoauth.PasswordAuthTest;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Arquillian.class)
public class MockOAuthTests {

    private static final Logger log = LoggerFactory.getLogger(MockOAuthTests.class);

    @Test
    public void runTests() throws Exception {
        try {
            logStart("MetricsTest :: Basic Metrics Tests");
            new MetricsTest().doTest();

            logStart("JaasClientConfigTest :: Client Configuration Tests");
            new JaasClientConfigTest().doTest();

            logStart("PasswordAuthTest :: Password Grant Tests");
            new PasswordAuthTest().doTest();

        } catch (Throwable e) {
            log.error("Exception has occured: ", e);
            throw e;
        }
    }


    private void logStart(String msg) {
        System.out.println();
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
        System.out.println();
    }
}
