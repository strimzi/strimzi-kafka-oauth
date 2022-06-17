/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UnprotectedTruststoreTest {

    private static final Logger log = LoggerFactory.getLogger(UnprotectedTruststoreTest.class);

    @Test
    public void doTest() throws Exception {

        // create a passwordless truststore
        KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(null, null);

        // Load the CA from crt file and add it to the truststore
        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(Files.newInputStream(Paths.get(getBaseDir() + "/../testsuite/docker/certificates/ca.crt")));
        truststore.setCertificateEntry("ca", cert);

        // save the truststore to disk without protecting it with the password
        File tmpStore = File.createTempFile("tmp_", ".p12");
        tmpStore.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tmpStore)) {
            truststore.store(fos, new char[0]);
        }

        // Worker thread, and vars for the test https server
        ExecutorService svc = Executors.newSingleThreadExecutor();
        CompletableFuture<SSLServerSocket> future = new CompletableFuture<>();
        SSLServerSocket server = null;

        try {
            // start a temporary web server
            svc.submit(new HttpsServer(truststore, future));

            // Wait for the server to start
            server = future.get();

            // try to connect without a truststore - should FAIL
            try {
                HttpUtil.get(URI.create("https://localhost:8078"), null, String.class);
                Assert.fail("Fetch without truststore should fail");

            } catch (SSLHandshakeException ignored) {
            }

            // try to use the unprotected truststore from disk
            SSLSocketFactory sslFactory = SSLUtil.createSSLFactory(tmpStore.getAbsolutePath(), null, null, KeyStore.getDefaultType(), null);
            try {
                HttpUtil.get(URI.create("https://localhost:8078"), sslFactory, null, String.class);
                Assert.fail("Fetch to localhost should fail due to non-matching certificate name");

            } catch (SSLHandshakeException e) {
                Assert.assertTrue("", e.getCause() instanceof CertificateException);
            }

            // use AnyHostHostnameVerifier, this should now successfully return content
            String msg = HttpUtil.get(URI.create("https://localhost:8078"), sslFactory, SSLUtil.createAnyHostHostnameVerifier(), null, String.class);
            Assert.assertEquals("Issue communicating with the server", "HELLO", msg);

        } finally {
            // shut down the test https server
            if (server != null) {
                server.close();
            }

            // shut down the thread pool
            svc.shutdown();
            if (!svc.awaitTermination(20, TimeUnit.SECONDS)) {
                log.debug("Internal error - the test https server failed to exit gracefully.");
            }
        }
    }

    private static File getBaseDir() {
        File base = new File(System.getProperty("user.dir"));
        if ("oauth-common".equals(base.getName())) {
            return base;
        }
        base = new File(base, "oauth-common");
        if (base.exists()) {
            return base;
        }
        throw new IllegalStateException("Unable to determine the path to 'oauth-common'");
    }

    static class HttpsServer implements Runnable {

        private final KeyStore truststore;
        private final CompletableFuture<SSLServerSocket> future;

        HttpsServer(KeyStore truststore, CompletableFuture<SSLServerSocket> future) {
            this.truststore = truststore;
            this.future = future;
        }

        public void run() {

            try {
                SSLContext ctx = SSLContext.getInstance("TLS");

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                InputStream keyFile = Files.newInputStream(Paths.get(getBaseDir() + "/../testsuite/docker/certificates/mockoauth.server.keystore.p12"));
                keyStore.load(keyFile, "changeit".toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "changeit".toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(truststore);

                ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                SSLServerSocketFactory factory = ctx.getServerSocketFactory();
                try (ServerSocket listener = factory.createServerSocket(8078)) {
                    SSLServerSocket serverSocket = (SSLServerSocket) listener;
                    future.complete(serverSocket);

                    while (true) {
                        try (Socket s = serverSocket.accept()) {
                            try {
                                try (PrintWriter out = new PrintWriter(s.getOutputStream(), false)) {
                                    out.print("HTTP/1.1 200 OK\nContent-Type: text/plain\nContent-Length: 5\n\nHELLO");
                                }
                            } catch (Exception ignored) {
                                break;
                            }
                        } catch (SocketException expected) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected exception: ", e);
                Assert.fail("Unexpected exception in the test https server");
            }
        }
    }
}
