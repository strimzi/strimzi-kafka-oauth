/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

class UserInfo {
    final String password;
    final Long expiresIn;

    public UserInfo(String password, Long expiresIn) {
        this.password = password;
        this.expiresIn = expiresIn;
    }
}
