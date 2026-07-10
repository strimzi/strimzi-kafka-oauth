/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

import java.util.HashSet;
import java.util.Set;

/**
 * SigningKeyProvider implementation for RSA and EC (Elliptic Curve) keys.
 * This provider uses the standard Java Cryptography Architecture (JCA) for key handling.
 */
public class JCASigningKeyProvider implements SigningKeyProvider {

    @Override
    public Set<Class<? extends JWK>> getSupportedJwkClasses() {
        Set<Class<? extends JWK>> classes = new HashSet<>();
        classes.add(RSAKey.class);
        classes.add(ECKey.class);
        return classes;
    }

    @Override
    public boolean supports(JWK jwk) {
        return jwk instanceof RSAKey || jwk instanceof ECKey;
    }

    @Override
    public SigningKey createSigningKey(JWK jwk) {
        try {
            if (jwk instanceof ECKey) {
                return new JCASigningKey(((ECKey) jwk).toPublicKey());
            } else if (jwk instanceof RSAKey) {
                return new JCASigningKey(((RSAKey) jwk).toPublicKey());
            }
            throw new IllegalArgumentException("Unsupported JWK type: " + jwk.getKeyType());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create signing key", e);
        }
    }
}
