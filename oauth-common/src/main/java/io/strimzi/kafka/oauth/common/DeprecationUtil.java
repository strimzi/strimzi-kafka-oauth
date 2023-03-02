/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;

/**
 * Helper class with methods to handle deprecated config options
 */
public class DeprecationUtil {

    /**
     * Get 'oauth.access.token.is.jwt' config option with fallback to the deprecated 'oauth.tokens.not.jwt'
     *
     * @param config a Config instance
     * @param log Logger to use to log warnings
     * @param errorPrefix Error message prefix that becomes part of ConfigException#getMessage() if both current,
     *                    and deprecated keys are configured
     * @return The value of the config option as a boolean
     */
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
