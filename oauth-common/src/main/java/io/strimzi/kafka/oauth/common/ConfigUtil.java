/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.util.Properties;

public class ConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    public static SSLSocketFactory createSSLFactory(Config config) {
        String truststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String truststoreData = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES);
        String password = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, truststoreData, password, type, rnd);
    }

    public static HostnameVerifier createHostnameVerifier(Config config) {
        String hostCheck = config.getValue(Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "HTTPS");

        // Following Kafka convention for skipping hostname validation (when set to <empty>)
        return "".equals(hostCheck) ? SSLUtil.createAnyHostHostnameVerifier() : null;
    }

    public static void putIfNotNull(Properties p, String key, Object value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    public static int getConnectTimeout(Config config) {
        return getTimeout(config, Config.OAUTH_CONNECT_TIMEOUT_SECONDS);
    }

    public static int getReadTimeout(Config config) {
        return getTimeout(config, Config.OAUTH_READ_TIMEOUT_SECONDS);
    }

    public static int getTimeout(Config config, String propertyKey) {
        int timeout = config.getValueAsInt(propertyKey, 60);
        if (timeout <= 0) {
            log.warn("The configured value of `" + propertyKey + "` (" + timeout + ") is <= 0 and will be ignored. Default used: 60 seconds");
            timeout = 60;
        }
        return timeout;
    }

    public static int getTimeoutConfigWithFallbackLookup(Config c, String key, String fallbackKey) {
        String result = c.getValue(key);
        if (result == null) {
            return getTimeout(c, fallbackKey);
        }
        return getTimeout(c, key);
    }

    public static String getConfigWithFallbackLookup(Config c, String key, String fallbackKey) {
        String result = c.getValue(key);
        if (result == null) {
            result = c.getValue(fallbackKey);
        }
        return result;
    }
}
