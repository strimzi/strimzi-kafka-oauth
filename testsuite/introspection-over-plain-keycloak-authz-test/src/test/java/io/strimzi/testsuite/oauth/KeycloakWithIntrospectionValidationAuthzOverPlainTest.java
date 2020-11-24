/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(Arquillian.class)
public class KeycloakWithIntrospectionValidationAuthzOverPlainTest extends KeycloakWithJwtValidationAuthzOverPlainTest {

    @Override
    protected String getTestTitle() {
        return "==== KeycloakWithIntrospectionValidationAuthzOverPlainTest - Tests Authorization ====";
    }

    @Test
    public void doTest() throws Exception {
        super.doTest();
    }
}
