/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.validator.TokenValidator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class Validators {

    /**
     * The registry of initialized TokenValidators
     */
    private final ConcurrentHashMap<ConfigurationKey, ValidatorEntry> registry = new ConcurrentHashMap<>();

    @SuppressFBWarnings("JLM_JSR166_UTILCONCURRENT_MONITOR")
    public TokenValidator get(ConfigurationKey key, Supplier<TokenValidator> factory) {
        synchronized (registry) {
            ValidatorEntry previous = registry.get(key);
            if (previous != null) {
                // If key with the same configId exists already it has to have an equal validatorKey (the same configuration)
                // In that case, the existing ValidatorEntry will be reused
                if (!key.getValidatorKey().equals(previous.key.getValidatorKey())) {
                    throw new RuntimeException("Configuration id " + key.getConfigId() + " with different configuration has already been assigned");
                }
                return previous.validator;
            }

            ValidatorEntry newEntry = new ValidatorEntry(key, factory.get());
            registry.put(key, newEntry);
            return newEntry.validator;
        }
    }

    private static class ValidatorEntry {
        final ConfigurationKey key;
        final TokenValidator validator;

        private ValidatorEntry(ConfigurationKey key, TokenValidator validator) {
            this.key = key;
            this.validator = validator;
        }
    }
}
