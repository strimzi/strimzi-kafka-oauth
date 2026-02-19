/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.clients;

import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.oauth.testsuite.common.LogLineReader;
import io.strimzi.oauth.testsuite.utils.TestUtil;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared MockOAuth server admin operations used by mockoauth and jdk17 tests.
 */
public class MockOAuthAdmin {

    public static String getMockOAuthAuthHostPort() {
        return System.getProperty("mockoauth.host", "localhost") + ":" +
                System.getProperty("mockoauth.port", "8090");
    }

    public static String getMockOAuthAdminHostPort() {
        return System.getProperty("mockoauth.host", "localhost") + ":" +
                System.getProperty("mockoauth.admin.port", "8091");
    }

    public static void changeAuthServerMode(String resource, String mode) throws IOException {
        String result = HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/" + resource + "?mode=" + mode), null, "text/plain", "", String.class);
        Assertions.assertEquals(mode.toUpperCase(Locale.ROOT), result, "admin server response should be ");
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
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/clients"),
                null,
                "application/json",
                "{\"clientId\": \"" + clientId + "\", \"secret\": \"" + secret + "\"}", String.class);
    }

    public static void createOAuthClientWithAssertion(String clientId, String clientAssertion) throws IOException {
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/clients"),
                null,
                "application/json",
                "{\"clientId\": \"" + clientId + "\", \"clientAssertion\": \"" + clientAssertion + "\"}", String.class);
    }

    public static void createOAuthUser(String username, String password) throws IOException {
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}", String.class);
    }

    public static void createOAuthUser(String username, String password, long expiresInSeconds) throws IOException {
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\", \"expires_in\": " + expiresInSeconds + "}", String.class);
    }

    public static void revokeToken(String token) throws IOException {
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/revocations"),
                null,
                "application/json",
                "{\"token\": \"" + token + "\"}", String.class);
    }

    public static void addGrantsForToken(String token, String grants) throws IOException {
        HttpUtil.post(URI.create("http://" + getMockOAuthAdminHostPort() + "/admin/grants_map"),
                null,
                "application/json",
                "{\"token\": \"" + token + "\", \"grants\": " + grants + "}", String.class);
    }

    public static void checkLog(LogLineReader logReader, String... args) throws IOException {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Args should be in pairs but there is an odd number of them.");
        }
        List<String> lines = logReader.readNext();

        // When the first pair acts as a header scope (empty second value), filter lines to only
        // the matching log block. This prevents false matches from background thread log entries
        // that happen to contain matching patterns (e.g., "clientId: null" from another context).
        // A log block consists of the header line plus all continuation lines (starting with
        // tab/whitespace) that follow it - matching the multi-line log.debug() output format.
        if (args.length > 2 && args[1].isEmpty()) {
            String headerRegex = args[0] + ":.*";
            Pattern headerPattern = Pattern.compile(headerRegex);

            int headerIdx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (headerPattern.matcher(lines.get(i)).find()) {
                    headerIdx = i;
                    break;
                }
            }

            if (headerIdx >= 0) {
                List<String> scopedLines = new ArrayList<>();
                scopedLines.add(lines.get(headerIdx));
                for (int i = headerIdx + 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith("\t") || line.startsWith("  ")) {
                        scopedLines.add(line);
                    } else {
                        break;
                    }
                }
                lines = scopedLines;
            }
        }

        for (int i = 0; i < args.length; i += 2) {
            Assertions.assertEquals(1, TestUtil.countLogForRegex(lines, args[i] + ":.*" + args[i + 1]), args[i] + " =~ " + args[i + 1]);
        }
    }
}
