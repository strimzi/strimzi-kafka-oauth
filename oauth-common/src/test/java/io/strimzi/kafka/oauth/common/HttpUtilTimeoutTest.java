/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import io.strimzi.kafka.oauth.validator.OAuthIntrospectionValidator;
import org.junit.Assert;
import org.junit.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class HttpUtilTimeoutTest {

    @Test
    public void testHttpTimeouts() throws Exception {
        CompletableFuture<ServerSocket> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                ServerSocket server = new ServerSocket(8079, 0, null);
                future.complete(server);

                Socket s = server.accept();

                Thread.sleep(20000);
                s.close();
            } catch (Throwable ignored) {
            }
        }).start();

        // Wait for the server to start
        future.get();

        int timeout = 5;
        System.setProperty("oauth.connect.timeout.seconds", String.valueOf(timeout));
        System.setProperty("oauth.read.timeout.seconds", String.valueOf(timeout));

        long start = System.currentTimeMillis();
        try {
            try {
                HttpUtil.get(URI.create("http://172.0.0.13:8079"), null, String.class);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("connect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }

            try {
                start = System.currentTimeMillis();
                HttpUtil.get(URI.create("http://localhost:8079"), null, String.class);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("Read timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }

            timeout = 2;
            try {
                start = System.currentTimeMillis();
                HttpUtil.get(URI.create("http://172.0.0.13:8079"), null, null, null, String.class, timeout, timeout);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("connect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }

            try {
                start = System.currentTimeMillis();
                HttpUtil.get(URI.create("http://localhost:8079"), null, null, null, String.class, timeout, timeout);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("Read timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }


            // Test validator
            try {
                OAuthIntrospectionValidator validator = new OAuthIntrospectionValidator("http://172.0.0.13:8079",
                        null, null, new PrincipalExtractor(), "http://172.0.0.13/", null, "Bearer",
                        "kafka", "kafka-secret", null, null, timeout, timeout, "token");

                start = System.currentTimeMillis();
                validator.validate("token");

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (Exception e) {
                Throwable cause = e.getCause();
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue(cause != null && cause instanceof SocketTimeoutException);
                Assert.assertTrue("Unexpected error: " + cause, cause.toString().contains("connect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }

        } finally {
            future.get().close();
        }
    }
}
