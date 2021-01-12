/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for OAuth authentication using Keycloak
 *
 * This test assumes there are multiple listeners configured with OAUTHBEARER SASL mechanism, but each configured differently
 * - configured with different options, or different realms. For OAuth over PLAIN tests the listeners are configured with PLAIN SASL mechanism.
 *
 * There is no authorization configured on the Kafka broker.
 */
@RunWith(Arquillian.class)
public class KeycloakAuthenticationTest {

    static final Logger log = LoggerFactory.getLogger(KeycloakAuthenticationTest.class);

    @Test
    public void doTest() throws Exception {
        try {
            BasicTests.doTests();
            OAuthOverPlainTests.doTests();
            AudienceTests.doTests();
            CustomCheckTests.doTests();
            MultiSaslTests.doTests();

        } catch (Throwable e) {
            log.error("Test failed: ", e);
            throw e;
        }
    }
}
