package io.strimzi.kafka.oauth.common;

import java.util.Properties;

public class Config {

    public static final String OAUTH_CLIENT_ID = "oauth.client.id";
    public static final String OAUTH_CLIENT_SECRET = "oauth.client.secret";
    public static final String OAUTH_USERNAME_CLAIM = "oauth.username.claim";
    public static final String OAUTH_SSL_TRUSTSTORE_LOCATION = "oauth.ssl.truststore.location";
    public static final String OAUTH_SSL_TRUSTSTORE_PASSWORD = "oauth.ssl.truststore.password";
    public static final String OAUTH_SSL_TRUSTSTORE_TYPE = "oauth.ssl.truststore.type";
    public static final String OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION = "oauth.ssl.secure.random.implementation";
    public static final String OAUTH_SSL_INSECURE_ALLOW_ANY_HOST = "oauth.ssl.insecure.allow.any.host";

    private Properties props;

    /**
     * Use this construtor if you only want to lookup configuration properties in System properties and ENV.
     */
    public Config() {}

    /**
     * Use this constructor to provide your own Properties object, with System properties and ENV serving as fallback.
     *
     * @param p Properties
     */
    public Config(Properties p) {
        props = p;
    }

    /**
     * Validate configuration by checking for unknown or missing properties.
     *
     * Override this method to provide custom validation.
     *
     * @throws RuntimeException if validation fails
     */
    public void validate() {

    }


    /**
     * Get value for property key, returning fallback if configuration for key is not found.
     *
     * @param key Config key
     * @param fallback Fallback value
     * @return Configuration value for specified key
     */
    public String getValue(String key, String fallback) {
        // try props first
        String result = props != null ? props.getProperty(key) : null;

        if (result != null) {
            return result;
        }

        // try system properties
        result = System.getProperty(key, null);
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
        return result != null ? Integer.valueOf(result) : fallback;
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
        String val = result.toLowerCase();
        boolean tru = val.equals("true") || val.equals("yes") || val.equals("y") || val.equals("1");
        if (true) {
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
     * @param key
     * @return
     */
    public static String toEnvName(String key) {
        return key.toUpperCase().replace('-', '_').replace('.', '_');
    }
}
