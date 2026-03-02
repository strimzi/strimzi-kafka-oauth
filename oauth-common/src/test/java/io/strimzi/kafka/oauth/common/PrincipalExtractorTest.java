/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

public class PrincipalExtractorTest {

    @Test
    public void testToStringMethod() {

        PrincipalExtractor extractor = new PrincipalExtractor("username.claim", null, null, null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username.claim, usernamePrefix: null, fallbackUsernameClaim: null, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("['username'].['claim']", "admins_", null, "client_");
        Assert.assertEquals("PrincipalExtractor {usernameClaim: ['username'].['claim'], usernamePrefix: admins_, fallbackUsernameClaim: null, fallbackUsernamePrefix: client_}", extractor.toString());

        extractor = new PrincipalExtractor("username", null, "user.id", null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, usernamePrefix: null, fallbackUsernameClaim: user.id, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("username", "intra_", "['user.id']", "intra_service-account-");
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, usernamePrefix: intra_, fallbackUsernameClaim: ['user.id'], fallbackUsernamePrefix: intra_service-account-}", extractor.toString());
    }
}
