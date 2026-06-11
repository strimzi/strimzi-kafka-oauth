/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import org.junit.Assert;
import org.junit.Test;

public class OAuthIntrospectionValidatorTest {

    @Test
    public void testBuildIntrospectionRequestBodyWithDefaultParamName() {
        Assert.assertEquals("token=abc", OAuthIntrospectionValidator.buildIntrospectionRequestBody("token", "abc"));
    }

    @Test
    public void testBuildIntrospectionRequestBodyWithCustomParamName() {
        Assert.assertEquals("access_token=abc", OAuthIntrospectionValidator.buildIntrospectionRequestBody("access_token", "abc"));
    }

    @Test
    public void testBuildIntrospectionRequestBodyUrlEncodesParamNameAndToken() {
        Assert.assertEquals("access+token%2B%26%3D=abc+123%2B%26%3D",
                OAuthIntrospectionValidator.buildIntrospectionRequestBody("access token+&=", "abc 123+&="));
    }
}
