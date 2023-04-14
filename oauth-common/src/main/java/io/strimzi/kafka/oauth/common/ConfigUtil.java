/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.util.List;
import java.util.Properties;

/**
 * The helper class with methods used in multiple places.
 */
public class ConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtil.class);

    /**
     * Create the <code>javax.net.ssl.SSLSocketFactory</code> from configuration in the passed <code>Config</code> object.
     *
     * @param config The Config object containing the configuration
     * @return The <code>javax.net.ssl.SSLSocketFactory</code> based on the passed configuration
     */
    public static SSLSocketFactory createSSLFactory(Config config) {
        String truststore = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String truststoreData = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES);
        String password = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = config.getValue(Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = config.getValue(Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, truststoreData, password, type, rnd);
    }

    /**
     * Create the HostnameVerifier from configuration in the passed <code>Config</code> object.
     *
     * @param config The Config object containing the configuration
     * @return The <code>javax.net.ssl.HostnameVerifier</code> based on the passed configuration
     */
    public static HostnameVerifier createHostnameVerifier(Config config) {
        String hostCheck = config.getValue(Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "HTTPS");

        // Following Kafka convention for skipping hostname validation (when set to <empty>)
        return "".equals(hostCheck) ? SSLUtil.createAnyHostHostnameVerifier() : null;
    }

    /**
     * Helper method that puts the key-value pair into the passed <code>java.util.Properties</code> only if
     * the value is not null.
     *
     * @param p Destination Properties object
     * @param key The key
     * @param value The value
     */
    public static void putIfNotNull(Properties p, String key, Object value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    /**
     * Resolve the value of the <code>Config.OAUTH_CONNECT_TIMEOUT_SECONDS</code> configuration
     *
     * @param config the Config object
     * @return Configured value as int
     */
    public static int getConnectTimeout(Config config) {
        return getTimeout(config, Config.OAUTH_CONNECT_TIMEOUT_SECONDS, null);
    }

    /**
     * Resolve the value of the <code>Config.OAUTH_READ_TIMEOUT_SECONDS</code> configuration
     *
     * @param config the Config object
     * @return Configured value as int
     */
    public static int getReadTimeout(Config config) {
        return getTimeout(config, Config.OAUTH_READ_TIMEOUT_SECONDS, null);
    }

    /**
     * Resolve the configuration value for the key as a timeout in seconds with the default value of 60
     *
     * @param c        the Config object
     * @param key      the configuration key
     * @param warnings a warnings list where any warnings should be added, and will later be logged as WARN
     *
     * @return Configured value as int
     */
    public static int getTimeout(Config c, String key, List<String> warnings) {
        int timeout = c.getValueAsInt(key, 60);
        if (timeout <= 0) {
            String msg = "The configured value of `" + key + "` (" + timeout + ") is <= 0 and will be ignored. Default used: 60 seconds";
            if (warnings != null) {
                warnings.add(msg);
            } else {
                log.warn(msg);
            }
            timeout = 60;
        }
        return timeout;
    }

    /**
     * Resolve the configuration value for the key as a timeout in seconds with the default value of 60.
     * If the key is not present, fallback to using a secondary key.
     *
     * @param c the Config object
     * @param key the configuration key
     * @param fallbackKey the fallback key
     * @param warnings a warnings list where any warnings should be added, and will later be logged as WARN
     * @return Configured value as int
     */
    public static int getTimeoutConfigWithFallbackLookup(Config c, String key, String fallbackKey, List<String> warnings) {
        String result = c.getValue(key);
        if (result == null) {
            return getTimeout(c, fallbackKey, warnings);
        }
        return getTimeout(c, key, warnings);
    }

    /**
     * Resolve the configuration value for the key as a string.
     * If the key is not present, fallback to using a secondary key.
     *
     * @param c the Config object
     * @param key the configuration key
     * @param fallbackKey the fallback key
     * @return Configured value as String
     */
    public static String getConfigWithFallbackLookup(Config c, String key, String fallbackKey) {
        String result = c.getValue(key);
        if (result == null) {
            result = c.getValue(fallbackKey);
        }
        return result;
    }
}
