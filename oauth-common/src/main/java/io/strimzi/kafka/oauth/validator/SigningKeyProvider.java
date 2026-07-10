/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.jwk.JWK;

import java.util.Set;

/**
 * Service provider interface for creating SigningKey instances from JWK objects.
 * Implementations are discovered via Java ServiceLoader mechanism.
 * <p>
 * This allows for pluggable support of different key types (RSA, EC, OKP, etc.)
 * without requiring all implementations to be present at build time.
 * </p>
 */
public interface SigningKeyProvider {

    /**
     * Returns the set of JWK class types this provider supports (e.g., RSAKey.class, ECKey.class, OctetKeyPair.class)
     *
     * @return the set of JWK class types
     */
    Set<Class<? extends JWK>> getSupportedJwkClasses();

    /**
     * Checks if this provider can handle the given JWK
     *
     * @param jwk the JWK to check
     * @return true if this provider supports the given JWK, false otherwise
     */
    boolean supports(JWK jwk);

    /**
     * Creates a SigningKey from the given JWK
     *
     * @param jwk the JWK to convert
     * @return a SigningKey instance
     * @throws IllegalArgumentException if the JWK is not supported or cannot be converted
     */
    SigningKey createSigningKey(JWK jwk);
}

