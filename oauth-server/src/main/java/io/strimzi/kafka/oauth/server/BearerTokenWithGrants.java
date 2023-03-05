/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class BearerTokenWithGrants implements BearerTokenWithPayload {

    private final TokenInfo ti;
    private volatile JsonNode payload;

    BearerTokenWithGrants(TokenInfo ti) {
        if (ti == null) {
            throw new IllegalArgumentException("TokenInfo == null");
        }
        this.ti = ti;
    }

    @Override
    public synchronized JsonNode getPayload() {
        return payload;
    }

    @Override
    public synchronized void setPayload(JsonNode value) {
        payload = value;
    }

    @Override
    public Set<String> getGroups() {
        return ti.groups();
    }

    @Override
    public ObjectNode getJSON() {
        return ti.payload();
    }

    @Override
    public String value() {
        return ti.token();
    }

    @Override
    public Set<String> scope() {
        return ti.scope();
    }

    @Override
    public long lifetimeMs() {
        return ti.expiresAtMs();
    }

    @Override
    public String principalName() {
        return ti.principal();
    }

    @Override
    public Long startTimeMs() {
        return ti.issuedAtMs();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BearerTokenWithGrants that = (BearerTokenWithGrants) o;
        return Objects.equals(ti, that.ti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ti);
    }

    @Override
    public String toString() {
        return "BearerTokenWithPayloadImpl (principalName: " + ti.principal() + ", groups: " + ti.groups() + ", lifetimeMs: " +
                ti.expiresAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.expiresAtMs()) + " UTC], startTimeMs: " +
                ti.issuedAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.issuedAtMs()) + " UTC], scope: " + ti.scope() + ")";
    }

    static class Serde {

        private static final String TOKEN = "t";
        private static final String SCOPES = "sc";
        private static final String GROUPS = "g";
        private static final String PRINCIPAL = "n";
        private static final String START_TIME = "st";
        private static final String EXPIRY_TIME = "e";
        private static final String TOKEN_CLAIMS = "j";
        private static final String EXTRA_PAYLOAD = "p";


        public byte[] serialize(BearerTokenWithGrants token) throws IOException {
            ObjectNode object = JSONUtil.newObjectNode();
            object.put(PRINCIPAL, token.principalName());
            JSONUtil.setArrayOfStringsIfNotNull(object, GROUPS, token.getGroups());
            JSONUtil.setArrayOfStringsIfNotNull(object, SCOPES, token.scope());
            object.put(TOKEN, token.value());
            object.put(START_TIME, token.startTimeMs());
            object.put(EXPIRY_TIME, token.lifetimeMs());
            object.set(TOKEN_CLAIMS, token.getJSON());
            object.set(EXTRA_PAYLOAD, token.getPayload());
            return JSONUtil.MAPPER.writeValueAsBytes(object);
        }

        public BearerTokenWithGrants deserialize(byte[] bytes) throws IOException {
            ObjectNode object = JSONUtil.MAPPER.readValue(bytes, ObjectNode.class);
            JsonNode groups = object.get(GROUPS);
            JsonNode scopes = object.get(SCOPES);
            JsonNode json = object.get(TOKEN_CLAIMS);
            JsonNode payload = object.get(EXTRA_PAYLOAD);
            BearerTokenWithGrants result = new BearerTokenWithGrants(
                    new TokenInfo(object.get(TOKEN).asText(),
                            scopes != null && scopes.isArray() ? new HashSet<>(JSONUtil.asListOfString(scopes, ",")) : null,
                            object.get(PRINCIPAL).asText(),
                            groups != null && groups.isArray() ? new HashSet<>(JSONUtil.asListOfString(groups, ",")) : null,
                            object.get(START_TIME).asLong(),
                            object.get(EXPIRY_TIME).asLong(),
                            json.isNull() ? null : json));

            if (!payload.isNull()) {
                result.setPayload(payload);
            }
            return result;
        }
    }
}
