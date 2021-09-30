/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.services.Credentials;
import io.strimzi.kafka.oauth.services.Principals;
import io.strimzi.kafka.oauth.services.Services;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SaslAuthenticationContext;
import org.apache.kafka.common.security.plain.internals.PlainSaslServer;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OAuthKafkaPrincipalBuilderTest {

    static final String USERNAME = "user";

    @Test
    public void testPreviousStoredPrincipalIsReused() {

        Services.configure(Collections.emptyMap());
        Credentials credentials = Services.getInstance().getCredentials();
        Principals principals = Services.getInstance().getPrincipals();

        // Simulate authentication using OAuth over PLAIN
        BearerTokenWithPayload token = mock(BearerTokenWithPayload.class);
        when(token.getPayload()).thenReturn("jwttoken");

        OAuthKafkaPrincipal authenticatedPrincipal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, USERNAME, token);
        credentials.storeCredentials(USERNAME, authenticatedPrincipal);

        // Simulate invocation of OAuthKafkaPrincipalBuilder
        OAuthKafkaPrincipalBuilder principalBuilder = new OAuthKafkaPrincipalBuilder();

        PlainSaslServer saslServer = mock(PlainSaslServer.class);
        when(saslServer.getAuthorizationID()).thenReturn(USERNAME);
        SaslAuthenticationContext context = mock(SaslAuthenticationContext.class);
        when(context.server()).thenReturn(saslServer);

        // Invoke the principal builder the first time
        KafkaPrincipal principal = principalBuilder.build(context);
        assertEquals("The Principal from authentication should be returned", authenticatedPrincipal, principal);
        assertNull("The Principal should have been taken from Credentials", credentials.takeCredentials(USERNAME));
        assertNotNull("The Principal should have been stored in Principals", principals.getPrincipal(saslServer));

        // Invoke the principal builder the second time
        principal = principalBuilder.build(context);
        assertEquals("The Principal from authentication should be returned", authenticatedPrincipal, principal);
    }
}