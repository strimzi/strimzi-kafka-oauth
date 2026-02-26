/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class OAuthKafkaPrincipalTest {

    @Test
    public void testEquals() {

        BearerTokenWithPayload token = new MockBearerTokenWithPayload("service-account-my-client", new HashSet<>(Arrays.asList("group1", "group2")),
                System.currentTimeMillis(), System.currentTimeMillis() + 60000, null, "BEARER-TOKEN-9823eh982u", JSONUtil.asJson("{}"));
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "service-account-my-client", token);


        BearerTokenWithPayload token2 = new MockBearerTokenWithPayload("bob", Collections.emptySet(),
                System.currentTimeMillis(), System.currentTimeMillis() + 60000, null, "BEARER-TOKEN-0000dd0000", null);
        OAuthKafkaPrincipal principal2 = new OAuthKafkaPrincipal("User", "service-account-my-client", token2);


        OAuthKafkaPrincipal principal3 = new OAuthKafkaPrincipal("User", "service-account-my-client", null);

        OAuthKafkaPrincipal principal4 = new OAuthKafkaPrincipal("User", "bob", null);


        Assertions.assertEquals(principal, principal2, "principal should be equal to principal2");
        Assertions.assertEquals(principal2, principal, "principal2 should be equal to principal");

        Assertions.assertEquals(principal, principal3, "principal should be equal to principal3");
        Assertions.assertEquals(principal3, principal, "principal3 should be equal to principal");

        Assertions.assertEquals(principal2, principal3, "principal2 should be equal to principal3");
        Assertions.assertEquals(principal3, principal2, "principal3 should be equal to principal2");

        Assertions.assertEquals(principal, principal, "principal should be equal to itself");
        Assertions.assertEquals(principal2, principal2, "principal2 should be equal to itself");
        Assertions.assertEquals(principal3, principal3, "principal3 should be equal to itself");
        Assertions.assertEquals(principal4, principal4, "principal4 should be equal to itself");

        Assertions.assertNotEquals(principal, principal4, "principal should not be equal to principal4");
        Assertions.assertNotEquals(principal4, principal, "principal4 should not be equal to principal");
        Assertions.assertNotEquals(principal3, principal4, "principal3 should not be equal to principal4");
        Assertions.assertNotEquals(principal4, principal3, "principal4 should not be equal to principal3");

        long hash1 = principal.hashCode();
        long hash2 = principal2.hashCode();
        long hash3 = principal3.hashCode();
        long hash4 = principal4.hashCode();

        Assertions.assertEquals(hash1, hash2, "Hashcode1 should be equal to hashcode2");
        Assertions.assertEquals(hash1, hash3, "Hashcode1 should be equal to hashcode3");
        Assertions.assertNotEquals(hash1, hash4, "Hashcode1 should not be equal to hashcode4");
    }

    @Test
    public void testJwtPrincipal() throws IOException {
        String rawToken = "token-ignored";
        String json = "{\"sub\": \"bob\", \"iss\": \"http://localhost/\", \"iat\": \"1635721200\", \"exp\": \"1667257200\"}";

        JsonNode parsed = JSONUtil.readJSON(json, JsonNode.class);
        TokenInfo tki = new TokenInfo(parsed, rawToken, "bob");
        BearerTokenWithPayload jwt = new BearerTokenWithJsonPayload(tki);
        OAuthKafkaPrincipal principalJwt = new OAuthKafkaPrincipal("User", "bob", jwt);

        Assertions.assertEquals(parsed, principalJwt.getJwt().getClaimsJSON(), "Can access parsed JWT");
    }

    @Test
    public void testSerialisation() throws IOException {

        OAuthKafkaPrincipalBuilder builder = new OAuthKafkaPrincipalBuilder();

        HashSet<String> groups = new HashSet<>();
        groups.add("admins");
        groups.add("operations");

        HashSet<String> scopes = new HashSet<>();
        scopes.add("offline");
        scopes.add("openid");

        long iat = System.currentTimeMillis() - 3600_000;
        long exp = System.currentTimeMillis() + 3600_000;
        String jwtBody = "{\"username\":\"alice\",\"exp\":" + exp / 1000 + ",\"groups\":[\"admins\",\"operations\"]}";
        TokenInfo tokenInfo = new TokenInfo("mock.accesstoken.", scopes, "alice", groups, iat, exp, JSONUtil.readJSON(jwtBody, ObjectNode.class));
        BearerTokenWithJsonPayload token = new BearerTokenWithJsonPayload(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "alice", token);


        byte[] serialized = builder.serialize(principal);
        KafkaPrincipal deserialized = builder.deserialize(serialized);

        Assertions.assertTrue(deserialized instanceof OAuthKafkaPrincipal, "Is OAuthKafkaPrincipal");
        OAuthKafkaPrincipal oauthDeserialized = (OAuthKafkaPrincipal) deserialized;
        Assertions.assertEquals(principal.getName(), oauthDeserialized.getName(), "name");
        Assertions.assertEquals(principal.getPrincipalType(), oauthDeserialized.getPrincipalType(), "type");
        Assertions.assertEquals(principal.getGroups(), oauthDeserialized.getGroups(), "groups");
        Assertions.assertEquals(principal.getJwt(), oauthDeserialized.getJwt(), "tokenInfo");
    }
}
