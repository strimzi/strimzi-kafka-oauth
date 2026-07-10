/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;

import java.security.PublicKey;

class JCASigningKey implements SigningKey {

    private static final DefaultJWSVerifierFactory VERIFIER_FACTORY = new DefaultJWSVerifierFactory();

    private final PublicKey publicKey;

    JCASigningKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public JWSVerifier createVerifier(JWSHeader header) throws JOSEException {
        return VERIFIER_FACTORY.createJWSVerifier(header, publicKey);
    }
}
