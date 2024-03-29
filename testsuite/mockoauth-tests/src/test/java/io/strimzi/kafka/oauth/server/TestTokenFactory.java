/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.TokenInfo;

public class TestTokenFactory {

    public static BearerTokenWithJsonPayload newTokenForUser(TokenInfo tokenInfo) {
        return new BearerTokenWithJsonPayload(tokenInfo);
    }
}
