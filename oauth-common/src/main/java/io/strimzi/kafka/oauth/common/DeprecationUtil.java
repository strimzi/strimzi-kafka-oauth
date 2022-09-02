/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;

public class DeprecationUtil {

    @SuppressWarnings("deprecation")
    public static boolean isAccessTokenJwt(Config config, Logger log, String errorPrefix) {
        String legacy = config.getValue(Config.OAUTH_TOKENS_NOT_JWT);
        if (legacy != null) {
            log.warn("Config option '{}' is deprecated. Use '{}' (with reverse meaning) instead.", Config.OAUTH_TOKENS_NOT_JWT, Config.OAUTH_ACCESS_TOKEN_IS_JWT);
            if (config.getValue(Config.OAUTH_ACCESS_TOKEN_IS_JWT) != null) {
                throw new ConfigException((errorPrefix != null ? errorPrefix : "") + "Can't use both '" + Config.OAUTH_ACCESS_TOKEN_IS_JWT + "' and '" + Config.OAUTH_TOKENS_NOT_JWT + "'");
            }
        }

        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(Config.OAUTH_ACCESS_TOKEN_IS_JWT, true);
    }
}
