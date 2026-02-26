/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;

import java.util.Objects;
import java.util.Set;

public class MockBearerTokenWithPayload implements BearerTokenWithPayload {

    private final TokenInfo ti;
    private JsonNode payload;

    public MockBearerTokenWithPayload(TokenInfo ti) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockBearerTokenWithPayload)) return false;
        MockBearerTokenWithPayload that = (MockBearerTokenWithPayload) o;
        return ti.equals(that.ti) && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ti, payload);
    }

    @Override
    public String toString() {
        return "BearerTokenWithPayload (principalName: " + ti.principal() + ", groups: " + ti.groups() + ", lifetimeMs: " +
            ti.expiresAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.expiresAtMs()) + " UTC], startTimeMs: " +
            ti.issuedAtMs() + " [" + TimeUtil.formatIsoDateTimeUTC(ti.issuedAtMs()) + " UTC], scope: " + ti.scope() + ")";
    }
}
