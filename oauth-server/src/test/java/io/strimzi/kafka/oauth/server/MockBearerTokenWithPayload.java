/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MockBearerTokenWithPayload implements BearerTokenWithPayload {


    private final String principalName;
    private final Set<String> groups;
    private final long createTime;
    private final long lifetime;
    private final Set<String> scopes;
    private final String token;
    private Object payload;

    MockBearerTokenWithPayload(String principalName, Set<String> groups, long createTime, long lifetime, String scope, String token, Object payload) {
        this.principalName = principalName;
        this.groups = groups;
        this.createTime = createTime;
        this.lifetime = lifetime;

        Set<String> scopesSet = new HashSet<>();
        String[] parsedScopes = scope != null ? scope.split(" ") : new String[0];
        for (String s: parsedScopes) {
            scopesSet.add(s);
        }
        scopes = Collections.unmodifiableSet(scopesSet);

        this.token = token;
        this.payload = payload;
    }

    @Override
    public Object getPayload() {
        return payload;
    }

    @Override
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public ObjectNode getJSON() {
        return null;
    }

    @Override
    public String value() {
        return token;
    }

    @Override
    public Set<String> scope() {
        return scopes;
    }

    @Override
    public long lifetimeMs() {
        return lifetime;
    }

    @Override
    public String principalName() {
        return principalName;
    }

    @Override
    public Long startTimeMs() {
        return createTime;
    }
}
