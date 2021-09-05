/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import java.util.Objects;

public class ConfigurationKey {

    private final ValidatorKey validatorKey;
    private final String configId;

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

    public String getConfigId() {
        return configId;
    }

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
}
