/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.util.Properties;

public class ConfigProperties {

    private Properties props;
    private Config config = new Config();

    public ConfigProperties() {
        this.props = new Properties();
    }

    public ConfigProperties(Properties overrides) {
        this.props = new Properties(overrides);
    }

    /**
     * This method first checks if any initial overrides are passed.
     * If not, it checks system property with the same name as key.
     * If it doesn't find it, it checks if ENV variable is set with the name:
     *
     *   key.toUpperCase().replace('-', '_').replace('.', '_');
     *
     * Finally, if nothing is found it sets the property to passed defaultValue.
     *
     * @param key
     * @param defaultValue
     */
    public void setProperty(String key, String defaultValue) {
        props.setProperty(key, config.getValue(key, defaultValue));
    }

    public Properties toProperties() {
        Properties p = new Properties();
        p.putAll(props);
        return p;
    }
}
