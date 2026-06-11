/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

public class IOUtilTest {

    @Test
    public void testTrimmedNonEmptyValueOrDefault() {
        Assert.assertEquals("token", IOUtil.trimmedNonEmptyValueOrDefault(null, "token"));
        Assert.assertEquals("token", IOUtil.trimmedNonEmptyValueOrDefault("", "token"));
        Assert.assertEquals("token", IOUtil.trimmedNonEmptyValueOrDefault("   ", "token"));
        Assert.assertEquals("access_token", IOUtil.trimmedNonEmptyValueOrDefault("  access_token  ", "token"));
        Assert.assertEquals("access_token", IOUtil.trimmedNonEmptyValueOrDefault("access_token", "token"));
    }
}
