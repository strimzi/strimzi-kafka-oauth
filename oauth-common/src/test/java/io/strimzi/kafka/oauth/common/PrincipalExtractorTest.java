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

        PrincipalExtractor extractor = new PrincipalExtractor("username.claim", null, null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username.claim, fallbackUsernameClaim: null, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("['username'].['claim']", null, null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: ['username'].['claim'], fallbackUsernameClaim: null, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("username", "user.id", null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, fallbackUsernameClaim: user.id, fallbackUsernamePrefix: null}", extractor.toString());

        extractor = new PrincipalExtractor("username", "['user.id']", null);
        Assert.assertEquals("PrincipalExtractor {usernameClaim: username, fallbackUsernameClaim: ['user.id'], fallbackUsernamePrefix: null}", extractor.toString());
    }
}
