/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.util.Properties;

public class ConfigProperties {

    private final Properties defaults;
    private final Config config;

    public ConfigProperties(Properties defaults) {
        this.defaults = defaults;
        config = new Config(defaults);
    }

    public Properties resolveTo(Properties destination) {
        for (Object key: defaults.keySet()) {
            destination.setProperty(key.toString(), config.getValue(key.toString()));
        }
        return destination;
    }

    public static void resolveAndExportToSystemProperties(Properties defaults) {
        Properties p = new ConfigProperties(defaults).resolveTo(new Properties());
        for (Object key: p.keySet()) {
            System.setProperty(key.toString(), p.getProperty(key.toString()));
        }
    }

    public static Properties resolve(Properties defaults) {
        return new ConfigProperties(defaults).resolveTo(new Properties());
    }
}
