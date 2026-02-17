/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

public class RefreshTokenInfo {
    final String clientId;
    final String username;

    public RefreshTokenInfo(String clientId, String username) {
        this.clientId = clientId;
        this.username = username;
    }
}
