/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static io.strimzi.kafka.oauth.common.JSONUtil.readJSON;

public class PrincipalExtractorTest {

    @Test
    public void testUsernameClaim() throws IOException {

        ObjectNode json = readJSON("{ " +
                "\"sub\": \"u123456\", " +
                "\"userId\": \"alice\", " +
                "\"user.id\": \"alice-123456\", " +
                "\"userInfo\": {\"id\": \"alice@example.com\" }, " +
                "\"user.info\": {\"sub.id\": \"alice-123456@example.com\"}, " +
                "\"foo'bar\": \"alice-in-wonderland\", " +
                "\"uname[1\": \"white-rabbit\"" +
                "}", ObjectNode.class);

        // 'userId' claim exists and is used as primary
        PrincipalExtractor extractor = new PrincipalExtractor("userId", null, null);
        Assert.assertEquals("'userId' top level works as primary", "alice", extractor.getPrincipal(json));

        // 'userId' claim exists and is used as fallback
        extractor = new PrincipalExtractor("nonexisting", "userId", null);
        Assert.assertEquals("'userId' top level works as fallback", "alice", extractor.getPrincipal(json));

        // 'userInfo.id' top level claim (not nested) does not exist
        extractor = new PrincipalExtractor("userInfo.id", null, null);
        Assert.assertNull("'userInfo.id' top level does not exist", extractor.getPrincipal(json));

        // 'userInfo.id' top level claim (not nested) does not exist
        extractor = new PrincipalExtractor("nonexisting", "userInfo.id", null);
        Assert.assertNull("'userInfo.id' top level does not exist", extractor.getPrincipal(json));

        // 'user.id' top level claim (not nested) exists and is used as primary
        extractor = new PrincipalExtractor("user.id", null, null);
        Assert.assertEquals("'user.id' top level works as primary", "alice-123456", extractor.getPrincipal(json));

        // 'user.id' top level claim (not nested) exists and is used as fallback
        extractor = new PrincipalExtractor("nonexisting", "user.id", null);
        Assert.assertEquals("'user.id' top level works as fallback", "alice-123456", extractor.getPrincipal(json));

        // 'user.id' top level claim (not nested) exists and is used as primary
        extractor = new PrincipalExtractor("[user.id]", null, null);
        Assert.assertEquals("[user.id] top level works as primary", "alice-123456", extractor.getPrincipal(json));

        // 'user.id' top level claim (not nested) exists and is used as fallback
        extractor = new PrincipalExtractor("nonexisting", "[user.id]", null);
        Assert.assertEquals("[user.id] top level works as fallback", "alice-123456", extractor.getPrincipal(json));

        // "'user.id'" top level claim (not nested) DOES NOT exist - notice quotes which are taken literally
        extractor = new PrincipalExtractor("'user.id'", null, null);
        Assert.assertNull("\"'user.id'\" (single-quoted) top level does not exist", extractor.getPrincipal(json));

        // "'user.id'" top level claim (not nested) DOES NOT exist - notice quotes which are taken literally
        extractor = new PrincipalExtractor("nonexisting", "'user.id'", null);
        Assert.assertNull("\"'user.id'\" (single-quoted) top level does not exist", extractor.getPrincipal(json));

        // 'user.info' top level claim exists, and 'sub.id' nested underneath exists and is used as primary
        extractor = new PrincipalExtractor("[user.info].[sub.id]", null, null);
        Assert.assertEquals("[user.info] top level, [sub.id] sub works as primary", "alice-123456@example.com", extractor.getPrincipal(json));

        // 'user.info' top level claim exists, but ' sub.id' does not exist - note the leading space
        extractor = new PrincipalExtractor("[ user.info] .[' sub.id']", null, null);
        Assert.assertNull("[ user.info] top level exists, [' sub.id'] does not exist (note the space within quotes)", extractor.getPrincipal(json));

        // 'user.info' top level claim exists, and 'sub.id' nested sub-claim exists and is used as a fallback
        extractor = new PrincipalExtractor("nonexisting", "[user.info] .  [ sub.id  ]", null);
        Assert.assertEquals("[user.info] .  [ sub.id ] works as fallback", "alice-123456@example.com", extractor.getPrincipal(json));

        // 'user.info' top level claim exists, and 'sub.id' nested underneath exists and is used as primary
        extractor = new PrincipalExtractor("['user.info'] . [ 'sub.id' ]", null, null);
        Assert.assertEquals("['user.info'] . [ 'sub.id' ] works as primary", "alice-123456@example.com", extractor.getPrincipal(json));

        // 'user.info' top level claim exists, and 'sub.id' nested underneath exists and is used as fallback
        extractor = new PrincipalExtractor("nonexisting", "  [    'user.info'] . ['sub.id'     ]", null);
        Assert.assertEquals("  [    'user.info'] . ['sub.id'     ] works as primary", "alice-123456@example.com", extractor.getPrincipal(json));


        extractor = new PrincipalExtractor("nonexisting", "nonexisting", null);
        Assert.assertNull("Non-existing claim", extractor.getPrincipal(json));

        // No closing ]
        try {
            new PrincipalExtractor("[user.id", "nonexisting", null);
            Assert.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Failed to parse username claim spec: '[user.id' (Missing ']')", e.toString().contains("Failed to parse username claim spec: '[user.id' (Missing ']')"));
        }

        // no dot between claim names
        try {
            new PrincipalExtractor("[user.info][user.id]", "nonexisting", null);
            Assert.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Failed to parse username claim spec: '[user.info][user.id]' (Missing '.' at position: 11)", e.toString().contains("Failed to parse username claim spec: '[user.info][user.id]' (Missing '.' at position: 11)"));
        }

        // once you start using [] you should use them consistently
        try {
            new PrincipalExtractor("[user.info].id", "nonexisting", null);
            Assert.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Failed to parse username claim spec: '[user.info].id' (Missing '[' at position:12)", e.toString().contains("Failed to parse username claim spec: '[user.info].id' (Missing '[' at position:12)"));
        }

        // 'user.id]' top level claim does not exist
        extractor = new PrincipalExtractor("user.id]", "nonexisting", null);
        Assert.assertNull("'user.id]' top level claim does not exist (note the ending square bracket)", extractor.getPrincipal(json));

        // '[foo].bar[baz]'
        try {
            extractor = new PrincipalExtractor("[foo].bar[baz]", "nonexisting", null);
            Assert.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Failed to parse username claim spec: '[foo].bar[baz]' (Expected '[' at position: 6)", e.toString().contains("Failed to parse username claim spec: '[foo].bar[baz]' (Expected '[' at position: 6)"));
        }

        // ['foo'bar]
        try {
            extractor = new PrincipalExtractor("['foo'bar]", "nonexisting", null);
            Assert.fail("Should have failed");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("Failed to parse username claim spec: missing ending quote [']: 'foo'bar", e.toString().contains("Failed to parse username claim spec: missing ending quote [']: 'foo'bar"));
        }

        // ['foo'bar']
        extractor = new PrincipalExtractor("['foo'bar']", "nonexisting", null);
        Assert.assertEquals("['foo'bar'] works as primary", "alice-in-wonderland", extractor.getPrincipal(json));

        // ['uname[1']
        extractor = new PrincipalExtractor("['uname[1']", "nonexisting", null);
        Assert.assertEquals("['uname[1'] works as primary", "white-rabbit", extractor.getPrincipal(json));
    }
}
