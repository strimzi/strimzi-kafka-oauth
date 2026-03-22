/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import io.strimzi.kafka.oauth.common.JSONUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

public class JWKSTest {

    @Test
    public void testParseJWKS() throws Exception {

        String bodyString = "{\"keys\":[" +
                "{\"kid\":\"PdbyxXXc7pwIX8xoIS7Kb-g1ZEDNFISpbyZs2MNkRJY\",\"kty\":\"EC\",\"alg\":\"ES256\",\"use\":\"sig\",\"crv\":\"P-256\",\"x\":\"MfgHBLCy6aKERQwnIu7FOJ1hCL0lwddinjnUW8-yVk4\",\"y\":\"vU_WKl6cln2M-HtKC_RVM2yq4oKjqrwu9EtMsrboAz0\"}]}";

        byte[] bodyInput = bodyString.getBytes(StandardCharsets.UTF_8);

        String body = JSONUtil.readJSON(new ByteArrayInputStream(bodyInput), String.class);

        String parsedKeyID = null;
        PublicKey publicKey = null;

        JWKSet jwks = JWKSet.parse(body);
        for (JWK jwk: jwks.getKeys()) {
            if (jwk.getKeyUse().equals(KeyUse.SIGNATURE)) {
                if (jwk instanceof ECKey) {
                    publicKey = ((ECKey) jwk).toPublicKey();
                } else if (jwk instanceof RSAKey) {
                    publicKey = ((RSAKey) jwk).toPublicKey();
                }
                parsedKeyID = jwk.getKeyID();
            }
        }

        Assert.assertNotNull(parsedKeyID);
        Assert.assertEquals("PdbyxXXc7pwIX8xoIS7Kb-g1ZEDNFISpbyZs2MNkRJY", parsedKeyID);
        Assert.assertNotNull(publicKey);
    }

    @Test
    public void testParseOKPKey() throws Exception {
        String keyId = "ed25519-test-key";
        String bodyString = "{\"keys\":[{\"kid\":\"" + keyId +
                "\",\"kty\":\"OKP\",\"alg\":\"EdDSA\",\"use\":\"sig\"," +
                "\"crv\":\"Ed25519\",\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}]}";

        JWKSet jwks = JWKSet.parse(bodyString);
        Assert.assertEquals(1, jwks.getKeys().size());

        JWK jwk = jwks.getKeys().iterator().next();
        Assert.assertEquals(KeyUse.SIGNATURE, jwk.getKeyUse());
        Assert.assertTrue(jwk instanceof OctetKeyPair);

        Assert.assertEquals(keyId, jwk.getKeyID());
        OctetKeyPair signingKey = (OctetKeyPair) jwk.toPublicJWK();
        Assert.assertNotNull(signingKey);
        Assert.assertEquals(Curve.Ed25519, signingKey.getCurve());
        Assert.assertFalse(signingKey.isPrivate());
    }

}
