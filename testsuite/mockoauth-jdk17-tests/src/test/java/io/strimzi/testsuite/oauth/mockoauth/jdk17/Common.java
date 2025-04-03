/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth.jdk17;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.testsuite.oauth.common.LogLineReader;
import io.strimzi.testsuite.oauth.common.TestUtil;
import org.junit.Assert;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class Common {

    public static final String LOG_PATH = "target/test.log";

    public static void changeAuthServerMode(String resource, String mode) throws IOException {
        String result = HttpUtil.post(URI.create("http://mockoauth:8091/admin/" + resource + "?mode=" + mode), null, "text/plain", "", String.class);
        Assert.assertEquals("admin server response should be ", mode.toUpperCase(Locale.ROOT), result);
        if ("server".equals(resource)) {
            try {
                // This is to work around a race condition when switching server mode
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Interrupted!");
            }
        }
    }

    public static void createOAuthClient(String clientId, String secret) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/clients"),
                null,
                "application/json",
                "{\"clientId\": \"" + clientId + "\", \"secret\": \"" + secret + "\"}", String.class);
    }

    public static void createOAuthUser(String username, String password) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}", String.class);
    }

    public static void createOAuthUser(String username, String password, long expiresInSeconds) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\", \"expires_in\": " + expiresInSeconds + "}", String.class);
    }

    public static void addGrantsForToken(String token, String grants) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/grants_map"),
                null,
                "application/json",
                "{\"token\": \"" + token + "\", \"grants\": " + grants + "}", String.class);
    }

    static void checkLog(LogLineReader logReader, String... args) throws IOException {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Args should be in pairs but there is an odd number of them.");
        }
        List<String> lines = logReader.readNext();

        for (int i = 0; i < args.length; i += 2) {
            Assert.assertEquals(args[i] + " =~ " + args[i + 1], 1, TestUtil.countLogForRegex(lines, args[i] + ":.*" + args[i + 1]));
        }
    }

    static class MockBearerTokenWithPayload implements BearerTokenWithPayload {

        private final TokenInfo ti;
        private JsonNode payload;

        MockBearerTokenWithPayload(TokenInfo ti) {
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
}
