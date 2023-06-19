/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import org.junit.Assert;
import org.junit.Test;

public class PrincipalExtractorTest {

    @Test
    public void testNestedClaim() {

        ObjectNode json = JSONUtil.newObjectNode();
        ObjectNode current = json.putObject("oauth");
        current = current.putObject("username");
        current.put("claim", "user1");
        current.put("fallbackClaim", "fallbackUser1");

        PrincipalExtractor extractor = new PrincipalExtractor("oauth.username.claim", null, null);
        String principal = extractor.getPrincipal(json);

        Assert.assertEquals("principal == user1", "user1", principal);

        extractor = new PrincipalExtractor("oauth.username.id", "oauth.username.fallbackClaim", null);
        principal = extractor.getPrincipal(json);

        Assert.assertEquals("fallback principal == fallbackUser1", "fallbackUser1", principal);
    }
}
