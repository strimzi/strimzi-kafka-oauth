/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import java.util.Locale;

public enum Endpoint {
    JWKS,
    INTROSPECT,
    USERINFO,
    TOKEN,
    SERVER,
    CLIENTS,
    USERS,
    REVOCATIONS,
    GRANTS,
    FAILING_GRANTS;

    public static Endpoint fromString(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
