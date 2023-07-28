/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.OAuthIntrospectionValidator;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class HttpUtilTimeoutTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtilTimeoutTest.class);

    private static final int FIRST_TIMEOUT = 5;
    static {
        System.setProperty("oauth.connect.timeout.seconds", String.valueOf(FIRST_TIMEOUT));
        System.setProperty("oauth.read.timeout.seconds", String.valueOf(FIRST_TIMEOUT));
    }

    public void doTest() throws Exception {
        Services.configure(Collections.emptyMap());

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

        // This sh
        int timeout = FIRST_TIMEOUT;

        long start = System.currentTimeMillis();
        try {
            try {
                HttpUtil.get(URI.create("http://192.168.255.255:26309"), null, String.class);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("onnect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            } catch (IOException e) {
                if (e.getCause() instanceof ConnectException) {
                    LOG.warn("Connect timeout test skipped due to immediate ConnectException");
                } else {
                    LOG.error("Unexpected exception: ", e);
                    Assert.fail();
                }
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
                HttpUtil.get(URI.create("http://192.168.255.255:26309"), null, null, null, String.class, timeout, timeout);

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (SocketTimeoutException e) {
                long diff = System.currentTimeMillis() - start;
                Assert.assertTrue("Unexpected error: " + e, e.toString().contains("onnect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            } catch (IOException e) {
                if (e.getCause() instanceof ConnectException) {
                    LOG.warn("Connect timeout test skipped due to immediate ConnectException");
                } else {
                    LOG.error("Unexpected exception: ", e);
                    Assert.fail();
                }
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
                OAuthIntrospectionValidator validator = new OAuthIntrospectionValidator("test", "http://192.168.255.255:26309",
                        null, null, new PrincipalExtractor(), null, null, "http://172.0.0.13/", null, "Bearer",
                        "kafka", "kafka-secret", null, null, timeout, timeout, false, 0, 0, true);

                start = System.currentTimeMillis();
                validator.validate("token");

                Assert.fail("Should fail with SocketTimeoutException");
            } catch (Exception e) {
                Throwable cause = e.getCause();
                long diff = System.currentTimeMillis() - start;

                Assert.assertTrue("Wrong exception: " + e + " caused by " + cause, cause instanceof SocketTimeoutException);
                Assert.assertTrue("Unexpected error: " + cause, cause.toString().contains("onnect timed out"));
                Assert.assertTrue("Unexpected diff: " + diff, diff >= timeout * 1000 && diff < timeout * 1000 + 1000);
            }

        } finally {
            future.get().close();
        }
    }
}
