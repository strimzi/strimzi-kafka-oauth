/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.junit.Assert;
import org.junit.Test;

public class OAuthKafkaPrincipalTest {

    @Test
    public void testEquals() {

        BearerTokenWithPayload token = new MockBearerTokenWithPayload("service-account-my-client",
                System.currentTimeMillis(), System.currentTimeMillis() + 60000, null, "BEARER-TOKEN-9823eh982u", "Whatever");
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "service-account-my-client", token);


        BearerTokenWithPayload token2 = new MockBearerTokenWithPayload("bob",
                System.currentTimeMillis(), System.currentTimeMillis() + 60000, null, "BEARER-TOKEN-0000dd0000", null);
        OAuthKafkaPrincipal principal2 = new OAuthKafkaPrincipal("User", "service-account-my-client", token2);


        OAuthKafkaPrincipal principal3 = new OAuthKafkaPrincipal("User", "service-account-my-client");

        OAuthKafkaPrincipal principal4 = new OAuthKafkaPrincipal("User", "bob");


        Assert.assertTrue("principal should be equal to principal2", principal.equals(principal2));
        Assert.assertTrue("principal2 should be equal to principal", principal2.equals(principal));

        Assert.assertTrue("principal should be equal to principal3", principal.equals(principal3));
        Assert.assertTrue("principal3 should be equal to principal", principal3.equals(principal));

        Assert.assertTrue("principal2 should be equal to principal3", principal2.equals(principal3));
        Assert.assertTrue("principal3 should be equal to principal2", principal3.equals(principal2));

        Assert.assertTrue("principal should be equal to itself", principal.equals(principal));
        Assert.assertTrue("principal2 should be equal to itself", principal2.equals(principal2));
        Assert.assertTrue("principal3 should be equal to itself", principal3.equals(principal3));
        Assert.assertTrue("principal4 should be equal to itself", principal4.equals(principal4));

        Assert.assertFalse("principal should not be equal to principal4", principal.equals(principal4));
        Assert.assertFalse("principal4 should not be equal to principal", principal4.equals(principal));
        Assert.assertFalse("principal3 should not be equal to principal4", principal3.equals(principal4));
        Assert.assertFalse("principal4 should not be equal to principal3", principal4.equals(principal3));

        long hash1 = principal.hashCode();
        long hash2 = principal2.hashCode();
        long hash3 = principal3.hashCode();
        long hash4 = principal4.hashCode();

        Assert.assertTrue("Hashcode1 should be equal to hashcode2", hash1 == hash2);
        Assert.assertTrue("Hashcode1 should be equal to hashcode3", hash1 == hash3);
        Assert.assertFalse("Hashcode1 should not be equal to hashcode4", hash1 == hash4);
    }
}
