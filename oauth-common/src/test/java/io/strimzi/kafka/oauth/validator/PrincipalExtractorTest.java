/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.InvalidPathException;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.jsonpath.JsonPathQueryException;
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
                "\"'user.id'\": \"quoted-alice-123456\", " +
                "\"$user.id\": \"$alice\", " +
                "\"$$user.id\": \"$$alice\", " +
                "\"$.user.id\": \"$.alice\", " +
                "\"userInfo\": {\"id\": \"alice@example.com\" }, " +
                "\"user.info\": {\"sub.id\": \"alice-123456@example.com\"}, " +
                "\"foo'bar\": \"alice-in-wonderland\", " +
                "\"uname[1\": \"white-rabbit\" " +
                "}", ObjectNode.class);

        String[] claimSpec = {
            // top-level userId attribute
            "userId", "alice",
            // top-level userInfo attribute (a misconfiguration as you hit the nested object in test json)
            // Should we log a warning and display a matched object rather than just return null (the result is failed authentication anyway)?
            "userInfo", null,
            // top-level userInfo.id attribute (no match)
            "userInfo.id", null,
            // nested id attribute under userInfo top-level attribute
            "['userInfo']['id']", "alice@example.com",
            // nested id attribute under userInfo top-level attribute
            "['userInfo']id", "alice@example.com",
            // nested id attribute under userInfo top-level attribute
            "['userInfo'].id", "alice@example.com",
            // nested id attribute under userInfo top-level attribute
            "[ 'userInfo' ][ 'id' ]", "alice@example.com",
            // nested id attribute under userInfo top-level attribute
            "[ 'userInfo' ].[ 'id' ]", "alice@example.com",
            // nested sub.id attribute under user.info top-level attribute
            "['user.info']['sub.id']", "alice-123456@example.com",
            // user.id top-level attribute
            "user.id", "alice-123456",
            // user.id top level attribute
            "['user.id']", "alice-123456",
            // $user.id top-level attribute
            "$user.id", "$alice",
            // $user.id top-level attribute
            "['$user.id']", "$alice",
            // $$user.id top-level attribute
            "$$user.id", "$$alice",
            // 'user.id' top-level attribute (quotes are part of the attribute name)
            "'user.id'", "quoted-alice-123456",
            // $['user.id'] top-level attribute (dollar, square brackets and quotes are part of the attribute name, no match)
            "$['user.id']", null,
            // uname[1 top-level attribute
            "['uname[1']", "white-rabbit",
            // uname]1 top-level attribute (no match)
            "['uname]1']", null,
            // user.id] top-level attribute (closing square bracket is part of the attribute name, no match)
            "user.id]", null,
            // foo'bar top-level attribute
            "foo'bar", "alice-in-wonderland",
            // foo'bar top-level attribute
            "['foo\\'bar']", "alice-in-wonderland",
            // baz attribute nested under bar, nested under top-level attribute foo (no match)
            "['foo']bar['baz']", null,
            // nested id attribute under user.info top level attribute (no match)
            "['user.info'].id", null,
            // nested sub.id attribute under user.info top-level attribute
            "['user.info'].['sub.id']", "alice-123456@example.com",
            // 'user.id' top-level attribute (quotes are part of the attribute name)
            "['\\'user.id\\'']", "quoted-alice-123456",
            // top-level $.user.id attribute
            "$.user.id", "$.alice",
            // top-level $.user.id attribute
            "['$.user.id']", "$.alice",
        };

        String[] claimSpecError = {
            // square brackets but no single quotes
            "[user.id]",
            // square brackets but no single quotes
            "[user.info][sub.id]",
            // opening square bracket but no single quote
            "[user.id",
            // opening square bracket but no single quote
            "[user.info].id",
            // opening square bracket but no single quote
            "[foo].bar[baz]",
            // third square bracket segment has no single quotes
            "['foo']bar[baz]",
            // ending single quote can only be followed by a closing square bracket
            "['foo'bar]",
            // ending single quote can only be followed by a closing square bracket (inner quote not escaped)
            "['foo'bar']",
            // ending single quote can only be followed by a closing square bracket (inner quotes not escaped)
            "[''user.id'']",
            // there should be no space between ] and [, only optional dot (.)
            "[ 'userInfo' ] [ 'id' ]"
        };

        for (int i = 0; i < claimSpec.length; i++) {
            String query = claimSpec[i];
            String expected = claimSpec[++i];

            try {
                PrincipalExtractor extractor = new PrincipalExtractor(query, null, null);
                Assert.assertEquals(query + " top level works as primary", expected, extractor.getPrincipal(json));

                extractor = new PrincipalExtractor("nonexisting", query, null);
                Assert.assertEquals(query + " top level works as fallback", expected, extractor.getPrincipal(json));
            } catch (Exception e) {
                throw new RuntimeException("Unexpected error while testing: " + query + " expecting it to return: " + expected, e);
            }
        }

        for (int i = 0; i < claimSpecError.length; i++) {
            String query = claimSpecError[i];
            try {
                PrincipalExtractor extractor = new PrincipalExtractor(query, "nonexisting", null);
                extractor.getPrincipal(json);
                Assert.fail("Should have failed");
            } catch (JsonPathQueryException e) {
                Assert.assertTrue("Cause instanceof InvalidPathException", e.getCause() instanceof InvalidPathException);
                // ignored
            }
        }
    }
}
