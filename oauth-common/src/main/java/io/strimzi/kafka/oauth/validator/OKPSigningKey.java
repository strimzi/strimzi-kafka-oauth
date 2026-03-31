/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;

class OKPSigningKey implements SigningKey {

    private final OctetKeyPair octetKeyPair;

    OKPSigningKey(OctetKeyPair octetKeyPair) {
        this.octetKeyPair = octetKeyPair;
    }

    @Override
    public JWSVerifier createVerifier(JWSHeader header) throws JOSEException {
        return new Ed25519Verifier(octetKeyPair);
    }
}
