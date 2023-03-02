/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.util.Properties;

/**
 * A helper class to apply configuration override mechanism to a <code>java.util.Properties</code> object.
 *
 * See {@link Config#getValue(String, String)}.
 */
public class ConfigProperties {

    private final Properties defaults;
    private final Config config;

    /**
     * Construct a new instance for existing <code>java.util.Properties</code> object
     *
     * @param defaults A <code>java.util.Properties</code> object that serves as the source of keys, and the final fallback
     *                 source for values if no override is found for a config option
     */
    public ConfigProperties(Properties defaults) {
        Properties p = new Properties();
        p.putAll(defaults);
        this.defaults = p;
        config = new Config(defaults);
    }

    /**
     * Apply the config override mechanism to all the keys in <code>java.util.Properties</code> object used to initialise
     * this <code>ConfigProperties</code> storing the resolved configuration values to the passed destination object.
     *
     * @param destination The destination <code>java.util.Properties</code> object
     * @return The passed destination object
     */
    public Properties resolveTo(Properties destination) {
        for (Object key: defaults.keySet()) {
            destination.setProperty(key.toString(), config.getValue(key.toString()));
        }
        return destination;
    }

    /**
     * Apply the config override mechanism to all the keys in the passed <code>java.util.Properties</code> object using it
     * as the final fallback for value resolution. The resolved config values are set as System properties.
     *
     * @param defaults A <code>java.util.Properties</code> object that serves as the source of keys, and the final fallback
     *                 source for values if no override is found for a config option
     */
    public static void resolveAndExportToSystemProperties(Properties defaults) {
        Properties p = new ConfigProperties(defaults).resolveTo(new Properties());
        for (Object key: p.keySet()) {
            System.setProperty(key.toString(), p.getProperty(key.toString()));
        }
    }

    /**
     * Apply the config override mechanism to all the keys in <code>java.util.Properties</code> object passed as defaults,
     * storing the resolved configuration values into a new <code>java.util.Properties</code> object that is returned as a result.
     *
     * @param defaults A <code>java.util.Properties</code> object that serves as the source of keys, and the final fallback
     *                 source for values if no override is found for a config option
     *
     * @return New <code>java.util.Properties</code> object with resolved configuration values
     */
    public static Properties resolve(Properties defaults) {
        return new ConfigProperties(defaults).resolveTo(new Properties());
    }
}
