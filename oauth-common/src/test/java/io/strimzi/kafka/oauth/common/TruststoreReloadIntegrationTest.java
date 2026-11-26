/*
 * Copyright 2017-2026, Strimzi authors.
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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that a truststore reload actually takes effect on a live TLS connection:
 * the client connects to a TLS server, the client truststore is swapped for one with
 * the wrong CA so the connection fails, and swapping the working truststore back
 * makes the connection succeed again.
 */
public class TruststoreReloadIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TruststoreReloadIntegrationTest.class);

    @Test
    public void testReloadTakesEffectOnLiveTlsConnection() throws Exception {
        File truststoreFile = File.createTempFile("truststore_", ".p12");
        truststoreFile.deleteOnExit();
        writeTruststore(truststoreFile, certificatePath("ca.crt"));
        long modified = System.currentTimeMillis();
        Assert.assertTrue(truststoreFile.setLastModified(modified));

        SSLSocketFactory factory = SSLUtil.createSSLFactory(truststoreFile.getAbsolutePath(), null, null,
                KeyStore.getDefaultType(), null);
        Assert.assertTrue("File based truststore should produce a ReloadableSSLSocketFactory",
                factory instanceof ReloadableSSLSocketFactory);

        ExecutorService svc = Executors.newSingleThreadExecutor();
        CompletableFuture<SSLServerSocket> future = new CompletableFuture<>();
        SSLServerSocket server = null;

        try {
            svc.submit(new TlsServer(future));
            server = future.get(30, TimeUnit.SECONDS);
            int port = server.getLocalPort();
            URI uri = URI.create("https://localhost:" + port);

            // working truststore: the TLS connection succeeds
            String msg = HttpUtil.get(uri, factory, SSLUtil.createAnyHostHostnameVerifier(), null, String.class);
            Assert.assertEquals("HELLO", msg);

            // swap in a truststore with the wrong CA: the reload triggers and the connection fails
            writeTruststore(truststoreFile, certificatePath("hydra.crt"));
            Assert.assertTrue(truststoreFile.setLastModified(modified + 10_000));
            try {
                HttpUtil.get(uri, factory, SSLUtil.createAnyHostHostnameVerifier(), null, String.class);
                Assert.fail("Connection with the wrong CA should fail");
            } catch (SSLHandshakeException expected) {
            }

            // swap the working truststore back: the reload triggers and the connection succeeds again
            writeTruststore(truststoreFile, certificatePath("ca.crt"));
            Assert.assertTrue(truststoreFile.setLastModified(modified + 20_000));
            msg = HttpUtil.get(uri, factory, SSLUtil.createAnyHostHostnameVerifier(), null, String.class);
            Assert.assertEquals("HELLO", msg);
        } finally {
            if (server != null) {
                server.close();
            }
            svc.shutdown();
            if (!svc.awaitTermination(20, TimeUnit.SECONDS)) {
                log.debug("The test TLS server failed to exit gracefully.");
            }
        }
    }

    private static void writeTruststore(File target, Path certPath) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);

        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(Files.newInputStream(certPath));
        store.setCertificateEntry("ca", cert);

        try (FileOutputStream fos = new FileOutputStream(target)) {
            store.store(fos, new char[0]);
        }
    }

    private static Path certificatePath(String name) {
        return Paths.get(getBaseDir() + "/../testsuite/docker/certificates/" + name);
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

    static class TlsServer implements Runnable {

        private final CompletableFuture<SSLServerSocket> future;

        TlsServer(CompletableFuture<SSLServerSocket> future) {
            this.future = future;
        }

        public void run() {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                InputStream keyFile = Files.newInputStream(Paths.get(getBaseDir()
                        + "/../testsuite/docker/certificates/mockoauth.server.keystore.p12"));
                keyStore.load(keyFile, "changeit".toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "changeit".toCharArray());

                ctx.init(kmf.getKeyManagers(), null, null);

                SSLServerSocketFactory factory = ctx.getServerSocketFactory();
                try (ServerSocket listener = factory.createServerSocket(0)) {
                    SSLServerSocket serverSocket = (SSLServerSocket) listener;
                    future.complete(serverSocket);

                    while (true) {
                        try (Socket raw = serverSocket.accept()) {
                            SSLSocket s = (SSLSocket) raw;
                            s.startHandshake();
                            try (PrintWriter out = new PrintWriter(s.getOutputStream(), false)) {
                                out.print("HTTP/1.1 200 OK\nContent-Type: text/plain\nContent-Length: 5\n\nHELLO");
                            }
                        } catch (SocketException expected) {
                            // server socket closed while waiting for a connection
                            break;
                        } catch (Exception e) {
                            // a failed handshake (or a dropped connection) ends this connection only
                            log.debug("Connection failed: {}", e.toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected exception: ", e);
                Assert.fail("Unexpected exception in the test TLS server");
            }
        }
    }
}
