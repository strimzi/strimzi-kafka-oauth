/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz;

public class OAuthOverPlainTest extends BasicTest {

    public OAuthOverPlainTest(String kafkaBootstrap, boolean oauthOverPlain) {
        super(kafkaBootstrap, oauthOverPlain);
    }
}
