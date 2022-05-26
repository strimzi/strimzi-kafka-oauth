/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import java.util.Locale;

enum Mode {
    MODE_200,
    MODE_400,
    MODE_401,
    MODE_403,
    MODE_404,
    MODE_500,
    MODE_503,
    MODE_OFF,
    MODE_CERT_ONE_ON,
    MODE_CERT_TWO_ON,
    MODE_EXPIRED_CERT_ON,
    MODE_JWKS_RSA_WITH_SIG_USE,
    MODE_JWKS_RSA_WITHOUT_SIG_USE;


    public static Mode fromString(String value) {
        return Mode.valueOf(value.toUpperCase(Locale.ROOT));
    }

}
