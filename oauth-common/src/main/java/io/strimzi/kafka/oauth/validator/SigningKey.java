/*
 * Copyright 2017-2025, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;

/**
 * Interface for creating JWS verifiers from signing keys.
 * Implementations provide the logic to create appropriate verifiers based on the key type.
 */
public interface SigningKey {
    /**
     * Create a JWS verifier for the given JWS header.
     *
     * @param header the JWS header containing algorithm and key information
     * @return a JWS verifier configured for this signing key
     * @throws JOSEException if the verifier cannot be created
     */
    JWSVerifier createVerifier(JWSHeader header) throws JOSEException;
}
