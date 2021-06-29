/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.testsuite.oauth.auth.Common.buildProducerConfigOAuthBearer;
import static io.strimzi.testsuite.oauth.auth.Common.loginWithUsernamePassword;

public class JwtManipulationTests {

    final String kafkaBootstrap = "kafka:9104";
    final String hostPort = "keycloak:8080";
    final String realm = "forge";

    void doTests() throws Exception {

        PrivateKey privateKey = generateSigningKey();

        String privatePem = getPrivateKeyAsPEM(privateKey);

        String adminToken = loginWithUsernamePassword(URI.create("http://" + hostPort + "/auth/realms/master/protocol/openid-connect/token"), "admin", "admin", "admin-cli");

        addRealmKey(adminToken, realm, privatePem);

        // fetch the added key info to get `kid` for creating the token
        String newKid = getKid(adminToken, realm);

        // Get the token for user
        String producerToken = getOriginalToken();

        SignedJWT producerJwt = SignedJWT.parse(producerToken);
        String kid = producerJwt.getHeader().getKeyID();
        Assert.assertEquals("kid should match", newKid, kid);

        testWithToken(producerToken);


        // Forge the token, copying information from original token as needed
        JsonNode producerJwtJSON = JSONUtil.asJson(producerJwt.getPayload().toJSONObject());

        HashMap<String, String> claims = new HashMap<>();
        claims.put("sub", producerJwtJSON.get("sub").asText());
        claims.put("iss", producerJwtJSON.get("iss").asText());
        claims.put("exp", producerJwtJSON.get("exp").asText());
        claims.put("typ", producerJwtJSON.get("typ").asText());
        claims.put("preferred_username", producerJwtJSON.get("preferred_username").asText());

        String signedToken = createSignedToken(kid, privateKey, claims);
        testWithToken(signedToken);


        // Let's test with more complex tokens
        HashMap<String, String> claims2 = (HashMap<String, String>) claims.clone();
        claims2.put("iat", String.valueOf(Instant.EPOCH.getEpochSecond()));
        claims2.put("nbf", claims2.get("iat"));
        claims2.put("aud", "00000002-0000-0000-c000-000000000000");
        claims2.put("aio", "42dgYNhtx6CvcEDLVOhQXIMeo/oCAA==");
        claims2.put("appid", "9e22feae-32d3-488c-be69-0e112149f19b");
        claims2.put("appidacr", "1");
        claims2.put("idp", "https://strimzi.io/xxx-xx-xxx/");
        claims2.put("oid", "0c5b0cdb-e305-4b59-ae6c-541c2a5b8592");
        claims2.put("tenant_region_scope", "region_eu_3");
        claims2.put("tid", "xxx-xx-xxx");
        claims2.put("uti", "sDnMmH4mbESWWJ302P8YAA");
        claims2.put("ver", "1.0");

        signedToken = createSignedToken(kid, privateKey, claims2);
        testWithToken(signedToken);


        // Test with the token that used to to break with Keycloak Common helper library
        HashMap<String, String> claims3 = (HashMap<String, String>) claims.clone();
        claims3.put("aud", "urn:company:kafka");
        claims3.put("iat", String.valueOf(Instant.EPOCH.getEpochSecond()));
        claims3.put("apptype", "Confidential");
        claims3.put("appid", "kafka-producer");
        claims3.put("authmethod", "http://strimzi.io/oidc/password");
        claims3.put("auth_time", "2021-05-25T10:00:00.000Z");
        claims3.put("ver", "1.0");

        signedToken = createSignedToken(kid, privateKey, claims3);
        testWithToken(signedToken);


        // Test using 'token_type' claim instead of 'typ' claim in the access token:
        // First remove 'typ' claim and see it fail
        HashMap<String, String> claims4 = (HashMap<String, String>) claims.clone();
        claims4.remove("typ");

        signedToken = createSignedToken(kid, privateKey, claims4);
        try {
            testWithToken(signedToken);
            Assert.fail("Should never be reached");
        } catch (Exception e) {
            Assert.assertTrue("Token type not set error", e.toString().contains("Token type not set"));
        }

        // Add 'token_typ' claim and see it pass
        claims4.put("token_type", "Bearer");

        signedToken = createSignedToken(kid, privateKey, claims4);
        testWithToken(signedToken);

    }

    private String getPrivateKeyAsPEM(PrivateKey privateKey) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(privateKey.getEncoded());
        out.close();

        Base64.Encoder encoder = Base64.getEncoder();
        String privateBaseEncoded = encoder.encodeToString(privateKey.getEncoded());

        return "-----BEGIN RSA PRIVATE KEY-----\\n" + privateBaseEncoded + "\\n-----END RSA PRIVATE KEY-----\\n";
    }

    private PrivateKey generateSigningKey() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        return keyPair.getPrivate();
    }

    private String createSignedToken(String kid, PrivateKey key, Map<String, String> claims) throws JOSEException {
        JWSSigner signer = new RSASSASigner(key);

        // Prepare JWT with claims set
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, String> entry: claims.entrySet()) {
            builder.claim(entry.getKey(), entry.getValue());
        }
        JWTClaimsSet claimsSet = builder.build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
                claimsSet);

        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private String getKid(String accessToken, String realm) throws IOException {
        JsonNode node = getActiveKeyInfo(accessToken, realm);

        String kid = null;
        ArrayNode keys = (ArrayNode) node.get("keys");
        for (JsonNode item: keys) {
            if (item.get("providerPriority").asInt() == 104) {
                kid = item.get("kid").asText();
                break;
            }
        }
        return kid;
    }

    private JsonNode getActiveKeyInfo(String accessToken, String realm) throws IOException {
        return HttpUtil.get(URI.create("http://" + hostPort + "/auth/admin/realms/" + realm + "/keys"), "Bearer " + accessToken, JsonNode.class);
    }

    private void addRealmKey(String accessToken, String realm, String keyPem) throws IOException {

        String authorization = "Bearer " + accessToken;

        ArrayNode realms = HttpUtil.get(URI.create("http://" + hostPort + "/auth/admin/realms"), authorization, ArrayNode.class);

        String realmId = null;
        for (JsonNode node: realms) {
            if (realm.equals(node.get("realm").asText())) {
                realmId = node.get("id").asText();
                break;
            }
        }

        Assert.assertNotNull("Realm not found", realmId);

        String body = "{\"name\":\"uploaded-rsa\",\"providerId\":\"rsa\",\"providerType\":\"org.keycloak.keys.KeyProvider\",\"parentId\":\"" + realmId +
                "\",\"config\":{\"priority\":[\"104\"],\"enabled\":[\"true\"],\"active\":[\"true\"],\"algorithm\":[\"RS256\"],\"privateKey\":[\"" + keyPem +
                "\"],\"certificate\":[]}}";

        HttpUtil.post(URI.create("http://" + hostPort + "/auth/admin/realms/" + realm + "/components"), "Bearer " + accessToken, "application/json", body, String.class);
    }


    private String getOriginalToken() throws IOException {

        final String tokenEndpointUri = "http://" + hostPort + "/auth/realms/" + realm + "/protocol/openid-connect/token";

        // first, request access token using client id and secret
        TokenInfo info = OAuthAuthenticator.loginWithClientSecret(URI.create(tokenEndpointUri), null, null,
                "kafka-producer-client", "kafka-producer-client-secret", true, null, null);

        return info.token();
    }

    private void testWithToken(String token) throws Exception {

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, token);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        Producer<String, String> producer = new KafkaProducer<>(producerProps);

        final String topic = "JwtManipulationTest-clientAccessTokenJwtRSAValidationTest";

        boolean success = false;
        int count = 3;
        do {
            try {
                producer.send(new ProducerRecord<>(topic, "The Message")).get();
                success = true;
            } catch (Exception e) {
                if (--count == 0) {
                    throw e;
                }
            }
        } while (!success);

        System.out.println("Produced The Message");
    }
}
