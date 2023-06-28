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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class BearerTokenWithJsonPayload implements BearerTokenWithPayload {

    private final static Logger log = LoggerFactory.getLogger(BearerTokenWithJsonPayload.class);

    private final TokenInfo ti;
    private volatile JsonNode payload;

    private int sessionId = System.identityHashCode(this);

    BearerTokenWithJsonPayload(TokenInfo ti) {
        if (ti == null) {
            throw new IllegalArgumentException("TokenInfo == null");
        }
        this.ti = ti;
    }

    @Override
    public JsonNode getPayload() {
        return payload;
    }

    @Override
    public void setPayload(JsonNode value) {
        payload = value;
    }

    @Override
    public Set<String> getGroups() {
        return ti.groups();
    }

    @Override
    public ObjectNode getClaimsJSON() {
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
    public int getSessionId() {
        return sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BearerTokenWithJsonPayload that = (BearerTokenWithJsonPayload) o;
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
                ti.issuedAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.issuedAtMs()) + " UTC], scope: " + ti.scope() + ", payload: " + ti.payload() + ", sessionId: " + sessionId + ")";
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
        private static final String SESSION_ID = "si";


        public byte[] serialize(BearerTokenWithJsonPayload token) throws IOException {
            ObjectNode object = JSONUtil.newObjectNode();
            object.put(PRINCIPAL, token.principalName());
            JSONUtil.setArrayOfStringsIfNotNull(object, GROUPS, token.getGroups());
            JSONUtil.setArrayOfStringsIfNotNull(object, SCOPES, token.scope());
            object.put(TOKEN, token.value());
            object.put(START_TIME, token.startTimeMs());
            object.put(EXPIRY_TIME, token.lifetimeMs());
            object.set(TOKEN_CLAIMS, token.getClaimsJSON());

            object.set(EXTRA_PAYLOAD, token.getPayload());
            if (token.getPayload() == null) {
                logTrace("Serialising a token without an extra payload: " + token);
            } else {
                logTrace("Serialising a token with an extra payload: " + token);
            }
            object.put(SESSION_ID, token.sessionId);

            logTrace("Serialising a token: {}", token);
            return JSONUtil.MAPPER.writeValueAsBytes(object);
        }

        public BearerTokenWithJsonPayload deserialize(byte[] bytes) throws IOException {
            ObjectNode object = JSONUtil.MAPPER.readValue(bytes, ObjectNode.class);
            JsonNode groups = object.get(GROUPS);
            JsonNode scopes = object.get(SCOPES);
            JsonNode json = object.get(TOKEN_CLAIMS);
            JsonNode payload = object.get(EXTRA_PAYLOAD);
            int sessionId = object.get(SESSION_ID).asInt();
            BearerTokenWithJsonPayload result = new BearerTokenWithJsonPayload(
                    new TokenInfo(object.get(TOKEN).asText(),
                            scopes != null && scopes.isArray() ? new HashSet<>(JSONUtil.asListOfString(scopes, ",")) : null,
                            object.get(PRINCIPAL).asText(),
                            groups != null && groups.isArray() ? new HashSet<>(JSONUtil.asListOfString(groups, ",")) : null,
                            object.get(START_TIME).asLong(),
                            object.get(EXPIRY_TIME).asLong(),
                            json.isNull() ? null : json));

            result.sessionId = sessionId;
            result.setPayload(payload);
            logTrace("Deserialised a token: {}", result);

            return result;
        }

        private void logTrace(String message, Object... args) {
            if (log.isTraceEnabled()) {
                log.trace(message, args);
            }
        }
    }
}
