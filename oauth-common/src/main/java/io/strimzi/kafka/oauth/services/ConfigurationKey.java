/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import java.util.Objects;

/**
 * JAAS configuration for the validator or the client can specify the {@code oauth.config.id} configuration property in
 * order to associate a configuration identifier with the metrics so that the metrics are then grouped, and can be queried
 * by that id.
 * <p>
 * If {@code oauth.config.id} is not specified a configuration id is calculated from other configuration parameters in a way
 * that is stable across application restarts.
 * <p>
 * The configuration id is set on the metrics as {@code context} attribute.
 * <p>
 * OAuth validators for the various listeners are cashed in a global singleton and indexed by the ConfigurationKey.
 * Usually multiple listener are configured differently and are each handled by its own instance of the validator.
 * But if more listeners are configured with exactly the same configuration parameters, they will share the same ConfigurationKey
 * and will all be handled my a single validator instance.
 */
public class ConfigurationKey {

    private final ValidatorKey validatorKey;
    private final String configId;

    /**
     * Create a new instance
     *
     * @param configId Configuration id for this validator
     * @param validatorKey Validator key for this validator
     */
    public ConfigurationKey(String configId, ValidatorKey validatorKey) {
        if (configId == null) {
            throw new IllegalArgumentException("configId == null");
        }
        if (validatorKey == null) {
            throw new IllegalArgumentException("validatorKey == null");
        }
        this.configId = configId;
        this.validatorKey = validatorKey;
    }

    /**
     * Get the config id
     *
     * @return The config id
     */
    public String getConfigId() {
        return configId;
    }

    /**
     * Get the validator key
     *
     * @return The validator key
     */
    public ValidatorKey getValidatorKey() {
        return validatorKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigurationKey that = (ConfigurationKey) o;
        return Objects.equals(configId, that.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configId);
    }

    @Override
    public String toString() {
        return "ConfigurationKey {configId: " + configId + ", validatorKey: " + validatorKey + "}";
    }
}
