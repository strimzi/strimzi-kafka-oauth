/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Configuration handling class
 */
public class Config {

    /** The name of 'oauth.client.id' config option  */
    public static final String OAUTH_CLIENT_ID = "oauth.client.id";

    /** The name of 'oauth.client.secret' config option  */
    public static final String OAUTH_CLIENT_SECRET = "oauth.client.secret";

    /** The name of 'oauth.client.credentials.grant.type.string' config option  */
    public static final String OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE = "oauth.client.credentials.grant.type";

    /** The fallback for 'oauth.client.credentials.grant.type.string' config option  */
    public static final String OAUTH_CLIENT_CREDENTIALS_GRANT_TYPE_FALLBACK = "client_credentials";


  /** The name of 'oauth.scope' config option  */
    public static final String OAUTH_SCOPE = "oauth.scope";

    /** The name of 'oauth.audience' config option  */
    public static final String OAUTH_AUDIENCE = "oauth.audience";

    /** The name of 'oauth.username.claim' config option  */
    public static final String OAUTH_USERNAME_CLAIM = "oauth.username.claim";

    /** The name of 'oauth.username.prefix' config option  */
    public static final String OAUTH_USERNAME_PREFIX = "oauth.username.prefix";

    /** The name of 'oauth.fallback.username.claim' config option  */
    public static final String OAUTH_FALLBACK_USERNAME_CLAIM = "oauth.fallback.username.claim";

    /** The name of 'oauth.fallback.username.prefix' config option  */
    public static final String OAUTH_FALLBACK_USERNAME_PREFIX = "oauth.fallback.username.prefix";

    /** The name of 'oauth.ssl.truststore.location' config option  */
    public static final String OAUTH_SSL_TRUSTSTORE_LOCATION = "oauth.ssl.truststore.location";

    /** The name of 'oauth.ssl.truststore.certificates' config option  */
    public static final String OAUTH_SSL_TRUSTSTORE_CERTIFICATES = "oauth.ssl.truststore.certificates";

    /** The name of 'oauth.ssl.truststore.password' config option  */

    public static final String OAUTH_SSL_TRUSTSTORE_PASSWORD = "oauth.ssl.truststore.password";

    /** The name of 'oauth.ssl.truststore.type' config option  */
    public static final String OAUTH_SSL_TRUSTSTORE_TYPE = "oauth.ssl.truststore.type";

    /** The name of 'oauth.ssl.secure.random.implementation' config option  */
    public static final String OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION = "oauth.ssl.secure.random.implementation";

    /** The name of 'oauth.ssl.endpoint.identification.algorithm' config option  */
    public static final String OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM = "oauth.ssl.endpoint.identification.algorithm";

    /** The name of 'oauth.access.token.is.jwt' config option */
    public static final String OAUTH_ACCESS_TOKEN_IS_JWT = "oauth.access.token.is.jwt";

    /** The name of 'oauth.connect.timeout.seconds' config option  */
    public static final String OAUTH_CONNECT_TIMEOUT_SECONDS = "oauth.connect.timeout.seconds";

    /** The name of 'oauth.read.timeout.seconds' config option  */
    public static final String OAUTH_READ_TIMEOUT_SECONDS = "oauth.read.timeout.seconds";

    /** The name of 'oauth.http.retries' config option  */
    public static final String OAUTH_HTTP_RETRIES = "oauth.http.retries";

    /** The name of 'oauth.http.retry.pause.millis' config option  */
    public static final String OAUTH_HTTP_RETRY_PAUSE_MILLIS = "oauth.http.retry.pause.millis";

    /** The name of 'oauth.config.id' config option  */
    public static final String OAUTH_CONFIG_ID = "oauth.config.id";

    /** The name of 'oauth.enable.metrics' config option  */
    public static final String OAUTH_ENABLE_METRICS = "oauth.enable.metrics";

    /**
     * Whether http requests should include "application/json" when being sent to the upstream OIDC server.
     */
    public static final String OAUTH_INCLUDE_ACCEPT_HEADER = "oauth.include.accept.header";

    /** The name of 'oauth.tokens.not.jwt' config option  */
    @Deprecated
    public static final String OAUTH_TOKENS_NOT_JWT = "oauth.tokens.not.jwt";

    private Map<String, ?> defaults;

    Config delegate;

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
        defaults = p.entrySet().stream().collect(
            Collectors.toMap(
                e -> String.valueOf(e.getKey()),
                e -> String.valueOf(e.getValue()),
                (v1, v2) -> v2, HashMap::new
            ));
    }

    /**
     * Use this constructor if you want to wrap another Config object and override some functionality
     * <p>
     * You only need to override {@link #getValue(String, String)} in your extending class.
     *
     * @param delegate The Config object to delegate to
     */
    public Config(Config delegate) {
        this.delegate = delegate;
    }

    /**
     * Use this constructor to provide default values in case some configuration is not set through system properties or ENV.
     *
     * @param p Default property values
     */
    public Config(Map<String, ?> p) {
        defaults = Collections.unmodifiableMap(p);
    }

    /**
     * Validate configuration by checking for unknown or missing properties.
     * <p>
     * Override this method to provide custom validation.
     *
     * @throws RuntimeException if validation fails
     */
    public void validate() {}

    /**
     * Get value for property key, returning fallback value if configuration for key is not found.
     * <p>
     * This method first checks if system property exists for the key.
     * If not, it checks if env variable exists with the name derived from the key:
     * <pre>
     *   key.toUpperCase().replace('-', '_').replace('.', '_');
     * </pre>
     * If not, it checks if env variable with name equal to key exists.
     * <p>
     * Ultimately, it checks the defaults passed at Config object construction time.
     * <p>
     * If no configuration is found for key, it returns the fallback value.
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Configuration value for specified key
     */
    public String getValue(String key, String fallback) {
        if (delegate != null) {
            return delegate.getValue(key, fallback);
        }

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
        if (defaults != null) {
            Object val = defaults.get(key);
            result = val != null ? String.valueOf(val) : null;
        }

        return result != null ? result : fallback;
    }

    /**
     * Get value for property key or null if not found
     *
     * @param key Config key
     * @return Config value
     */
    public final String getValue(String key) {
        return getValue(key, null);
    }

    /**
     * Get value for property key as int or fallback value if not found
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Config value
     */
    public final int getValueAsInt(String key, int fallback) {
        String result = getValue(key);
        return result != null ? Integer.parseInt(result) : fallback;
    }

    /**
     * Get value for property key as long or fallback value if not found
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Config value
     */
    public final long getValueAsLong(String key, long fallback) {
        String result = getValue(key);
        return result != null ? Long.parseLong(result) : fallback;
    }

    /**
     * Get value for property key as boolean or fallback value if not found
     * <p>
     * Valid values are: "true", "false", "yes", "no", "y", "n", "1", "0"
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Config value
     */
    public final boolean getValueAsBoolean(String key, boolean fallback) {
        String result = getValue(key);
        try {
            return result != null ? isTrue(result) : fallback;
        } catch (Exception e) {
            throw new ConfigException("Bad boolean value for key: " + key + ", value: " + result);
        }
    }

    /**
     * Get value for property key as a URI
     *
     * @param key Config key
     * @return Config value
     */
    public final URI getValueAsURI(String key) {
        String result = getValue(key);
        try {
            return URI.create(result);
        } catch (Exception e) {
            throw new ConfigException("Bad URI value for key: " + key + ", value: " + result, e);
        }
    }

    /**
     * Helper method the test if some boolean config option is set to 'true' or 'false'
     *
     * @param result The configured value of the option as String
     * @return The boolean value of the option
     */
    public static boolean isTrue(String result) {
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
     * <p>
     * Property key is converted to all uppercase, then all '.' and '-' characters are converted to '_'
     *
     * @param key   A key of a property which should be converted to environment variable name
     *
     * @return  A name which should be used for environment variable
     */
    public static String toEnvName(String key) {
        return key.toUpperCase(Locale.ENGLISH).replace('-', '_').replace('.', '_');
    }
}
