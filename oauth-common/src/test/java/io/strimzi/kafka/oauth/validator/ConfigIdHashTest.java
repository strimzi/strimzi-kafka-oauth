/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.ValidatorKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigIdHashTest {

    @Test
    public void testValidatorKey() {

        ValidatorKey vkey = getKey(null, null);
        ValidatorKey vkey2 = getKey(null, null);

        Assertions.assertEquals("f433dbf8", vkey.getConfigIdHash(), "Config id hash mismatch");
        Assertions.assertEquals(vkey.getConfigIdHash(), vkey2.getConfigIdHash(), "Config id hash should be the same");

        ValidatorKey key3 = getKey("group", null);
        ValidatorKey key4 = getKey(null, "group");

        Assertions.assertEquals("024dcc79", key3.getConfigIdHash(), "Config id hash mismatch");
        Assertions.assertEquals("dfec1585", key4.getConfigIdHash(), "Config id hash mismatch");
    }

    ValidatorKey getKey(String groupQuery, String groupDelimiter) {
        return new ValidatorKey.IntrospectionValidatorKey(
                "example-client",
                "example-client-secret",
                null,
                "http://mockoauth:8080",
                null,
                "@.aud='http://example.com/'",
                "preferred_username",
                null,
                null,
                groupQuery,
                groupDelimiter,
                null,
                null,
                null,
                null,
                false,
                "http://mockoauth:8080/introspect",
                null,
                null,
                60,
                60,
                true,
                0,
                0,
                false);
    }
}
