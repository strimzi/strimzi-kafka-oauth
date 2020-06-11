/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;

public class SessionInfo {

    private OAuthBearerToken token;
    private JsonNode grants;

    public OAuthBearerToken getToken() {
        return token;
    }

    public void setToken(OAuthBearerToken token) {
        this.token = token;
    }

    public JsonNode getGrants() {
        return grants;
    }

    public void setGrants(JsonNode grants) {
        this.grants = grants;
    }
}
