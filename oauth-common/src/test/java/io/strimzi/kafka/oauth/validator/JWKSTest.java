/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
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
}
