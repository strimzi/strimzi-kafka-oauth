/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.util.Locale;
import java.util.Properties;

public class Config {

    public static final String OAUTH_CLIENT_ID = "oauth.client.id";
    public static final String OAUTH_CLIENT_SECRET = "oauth.client.secret";
    public static final String OAUTH_USERNAME_CLAIM = "oauth.username.claim";
    public static final String OAUTH_SSL_TRUSTSTORE_LOCATION = "oauth.ssl.truststore.location";
    public static final String OAUTH_SSL_TRUSTSTORE_PASSWORD = "oauth.ssl.truststore.password";
    public static final String OAUTH_SSL_TRUSTSTORE_TYPE = "oauth.ssl.truststore.type";
    public static final String OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION = "oauth.ssl.secure.random.implementation";
    public static final String OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM = "oauth.ssl.endpoint.identification.algorithm";
    public static final String OAUTH_TOKENS_NOT_JWT = "oauth.tokens.not.jwt";

    private Properties defaults;

    /**
     * Use this construtor if you only want to lookup configuration in system properties and env
     * without any default configuration.
     */
    public Config() {}

    /**
     * Use this constructor to provide default values in case some configuration is not set through system properties or ENV.
     *
     * @param p Default property values
     */
    public Config(Properties p) {
        defaults = p;
    }

    /**
     * Validate configuration by checking for unknown or missing properties.
     *
     * Override this method to provide custom validation.
     *
     * @throws RuntimeException if validation fails
     */
    public void validate() {}

    /**
     * Get value for property key, returning fallback value if configuration for key is not found.
     *
     * This method first checks if system property exists for the key.
     * If not, it checks if env variable exists with the name derived from the key:
     *
     *   key.toUpperCase().replace('-', '_').replace('.', '_');
     *
     * If not, it checks if env variable with name equal to key exists.
     * Ultimately, it checks the defaults passed at Config object construction time.
     *
     * If no configuration is found for key, it returns the fallback value.
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Configuration value for specified key
     */
    public String getValue(String key, String fallback) {

        // try system properties first
        String result = System.getProperty(key, null);
        if (result != null) {
            return result;
        }

        // try env properties
        result = System.getenv(toEnvName(key));
        if (result != null) {
            return result;
        }

        // try env property by key name (without converting with toEnvName())
        result = System.getenv(key);
        if (result != null) {
            return result;
        }

        // try default properties and if all else fails return fallback value
        result = defaults != null ? defaults.getProperty(key) : null;

        return result != null ? result : fallback;
    }

    /**
     * Get value for property key or null if not found
     *
     * @param key Config key
     * @return Config value
     */
    public String getValue(String key) {
        return getValue(key, null);
    }

    /**
     * Get value for property key as int or fallback value if not found
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Config value
     */
    public int getValueAsInt(String key, int fallback) {
        String result = getValue(key);
        return result != null ? Integer.parseInt(result) : fallback;
    }

    /**
     * Get value for property key as boolean or fallback value if not found
     *
     * Valid values are: "true", "false", "yes", "no", "y", "n", "1", "0"
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Config value
     */
    public boolean getValueAsBoolean(String key, boolean fallback) {
        String result = getValue(key);
        try {
            return result != null ? isTrue(result) : fallback;
        } catch (Exception e) {
            throw new RuntimeException("Bad boolean value for key: " + key + ", value: " + result);
        }
    }

    private boolean isTrue(String result) {
        String val = result.toLowerCase(Locale.ENGLISH);
        if (val.equals("true") || val.equals("yes") || val.equals("y") || val.equals("1")) {
            return true;
        }
        if (val.equals("false") || val.equals("no") || val.equals("n") || val.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("Bad boolean value: " + result);
    }

    /**
     * Convert property key to env key.
     *
     * Property key is converted to all uppercase, then all '.' and '-' characters are converted to '_'
     *
     * @param key   A key of a property which should be converted to environment variable name
     *
     * @return  A name whihc should be used for environment variable
     */
    public static String toEnvName(String key) {
        return key.toUpperCase(Locale.ENGLISH).replace('-', '_').replace('.', '_');
    }
}
