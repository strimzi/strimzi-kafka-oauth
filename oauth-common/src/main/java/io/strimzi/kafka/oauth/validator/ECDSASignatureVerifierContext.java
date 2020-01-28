/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.keycloak.common.VerificationException;
import org.keycloak.crypto.AsymmetricSignatureVerifierContext;
import org.keycloak.crypto.KeyWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * This class provides ECDSA signature verification support.
 *
 * Adapted from: https://github.com/keycloak/keycloak/blob/8.0.1/services/src/main/java/org/keycloak/crypto/ClientECDSASignatureVerifierContext.java
 */
public class ECDSASignatureVerifierContext extends AsymmetricSignatureVerifierContext {

    public ECDSASignatureVerifierContext(KeyWrapper key) {
        super(key);
    }

    @Override
    public boolean verify(byte[] data, byte[] signature) throws VerificationException {
       /*
       Fallback for backwards compatibility of ECDSA signed tokens which were issued in previous versions.
       TODO remove by https://issues.jboss.org/browse/KEYCLOAK-11911
        */
        int expectedSize = ECDSA.valueOf(getAlgorithm()).getSignatureLength();
        byte[] derSignature = expectedSize != signature.length && signature[0] == 0x30 ? signature : concatenatedRSToASN1DER(signature, expectedSize);
        return super.verify(data, derSignature);
    }

    enum ECDSA {
        ES256(64),
        ES384(96),
        ES512(132);

        private final int signatureLength;

        ECDSA(int signatureLength) {
            this.signatureLength = signatureLength;
        }

        public int getSignatureLength() {
            return this.signatureLength;
        }
    }

    static byte[] concatenatedRSToASN1DER(final byte[] signature, int signLength) {
        int len = signLength / 2;
        int arraySize = len + 1;

        byte[] r = new byte[arraySize];
        byte[] s = new byte[arraySize];
        System.arraycopy(signature, 0, r, 1, len);
        System.arraycopy(signature, len, s, 1, len);
        BigInteger rBigInteger = new BigInteger(r);
        BigInteger sBigInteger = new BigInteger(s);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DERSequenceGenerator seqGen = new DERSequenceGenerator(bos);

            seqGen.addObject(new ASN1Integer(rBigInteger.toByteArray()));
            seqGen.addObject(new ASN1Integer(sBigInteger.toByteArray()));
            seqGen.close();
            bos.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate ASN.1 DER signature", e);
        }
        return bos.toByteArray();
    }
}