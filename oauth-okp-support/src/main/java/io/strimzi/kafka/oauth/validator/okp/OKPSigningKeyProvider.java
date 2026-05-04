/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator.okp;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import io.strimzi.kafka.oauth.validator.SigningKey;
import io.strimzi.kafka.oauth.validator.SigningKeyProvider;

import java.util.Collections;
import java.util.Set;

/**
 * SigningKeyProvider implementation for OKP (Octet Key Pair) keys.
 * Currently supports Ed25519 curve only.
 */
public class OKPSigningKeyProvider implements SigningKeyProvider {

    @Override
    public Set<Class<? extends JWK>> getSupportedJwkClasses() {
        return Collections.singleton(OctetKeyPair.class);
    }

    @Override
    public boolean supports(JWK jwk) {
        if (!(jwk instanceof OctetKeyPair)) {
            return false;
        }
        OctetKeyPair okp = (OctetKeyPair) jwk;
        return Curve.Ed25519.equals(okp.getCurve());
    }

    @Override
    public SigningKey createSigningKey(JWK jwk) {
        if (!supports(jwk)) {
            OctetKeyPair okp = (OctetKeyPair) jwk;
            throw new IllegalArgumentException("Unsupported OKP curve: " + okp.getCurve());
        }
        try {
            return new OKPSigningKey(((OctetKeyPair) jwk).toPublicJWK());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create OKP signing key", e);
        }
    }
}

