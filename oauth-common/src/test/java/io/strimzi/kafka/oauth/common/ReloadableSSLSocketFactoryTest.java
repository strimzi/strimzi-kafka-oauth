/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ReloadableSSLSocketFactoryTest {

    @Test
    public void testDoesNotReloadWhenFileUnchanged() throws Exception {
        File file = createTempTruststoreFile();
        AtomicInteger calls = new AtomicInteger();
        ReloadableSSLSocketFactory factory = new ReloadableSSLSocketFactory(file, countingSupplier(calls));

        SSLSocketFactory delegate = factory.currentDelegate();
        factory.createSocket().close();
        factory.createSocket().close();

        Assert.assertEquals("Delegate should only be created once", 1, calls.get());
        Assert.assertSame(delegate, factory.currentDelegate());
    }

    @Test
    public void testReloadsWhenTruststoreFileChanges() throws Exception {
        File file = createTempTruststoreFile();
        long modified = System.currentTimeMillis();
        Assert.assertTrue(file.setLastModified(modified));

        AtomicInteger calls = new AtomicInteger();
        ReloadableSSLSocketFactory factory = new ReloadableSSLSocketFactory(file, countingSupplier(calls));
        SSLSocketFactory delegate = factory.currentDelegate();

        Assert.assertTrue(file.setLastModified(modified + 10_000));
        factory.createSocket().close();

        Assert.assertEquals("Delegate should have been recreated", 2, calls.get());
        Assert.assertNotSame(delegate, factory.currentDelegate());

        // no further reload without another change
        factory.createSocket().close();
        Assert.assertEquals(2, calls.get());
    }

    @Test
    public void testFailedReloadKeepsPreviousFactory() throws Exception {
        File file = createTempTruststoreFile();
        long modified = System.currentTimeMillis();
        Assert.assertTrue(file.setLastModified(modified));

        AtomicInteger calls = new AtomicInteger();
        Supplier<SSLSocketFactory> supplier = () -> {
            if (calls.incrementAndGet() == 2) {
                throw new ConfigException("Simulated reload failure");
            }
            return newDelegateFactory();
        };

        ReloadableSSLSocketFactory factory = new ReloadableSSLSocketFactory(file, supplier);
        SSLSocketFactory delegate = factory.currentDelegate();

        // the reload fails: the previous delegate is kept and createSocket() still works
        Assert.assertTrue(file.setLastModified(modified + 10_000));
        factory.createSocket().close();
        Assert.assertEquals(2, calls.get());
        Assert.assertSame(delegate, factory.currentDelegate());

        // the next change retries the reload, and this time it succeeds
        Assert.assertTrue(file.setLastModified(modified + 20_000));
        factory.createSocket().close();
        Assert.assertEquals(3, calls.get());
        Assert.assertNotSame(delegate, factory.currentDelegate());
    }

    @Test
    public void testFileBasedTruststoreIsReloadable() throws Exception {
        File store = File.createTempFile("truststore_", ".p12");
        store.deleteOnExit();
        writeTruststore(store, certificatePath("ca.crt"));

        SSLSocketFactory factory = SSLUtil.createSSLFactory(store.getAbsolutePath(), null, null, null, null);

        Assert.assertTrue("File based truststore should produce a ReloadableSSLSocketFactory",
                factory instanceof ReloadableSSLSocketFactory);
        factory.createSocket().close();
    }

    @Test
    public void testPemFileTruststoreIsReloadable() throws Exception {
        SSLSocketFactory factory = SSLUtil.createSSLFactory(certificatePath("ca.crt").toString(), null, null, "pem", null);

        Assert.assertTrue("File based PEM truststore should produce a ReloadableSSLSocketFactory",
                factory instanceof ReloadableSSLSocketFactory);
        factory.createSocket().close();
    }

    @Test
    public void testPemDataTruststoreIsNotReloadable() throws Exception {
        String pemData = Files.readString(certificatePath("ca.crt"));

        SSLSocketFactory factory = SSLUtil.createSSLFactory(null, pemData, null, "pem", null);

        Assert.assertNotNull(factory);
        Assert.assertFalse("PEM data truststore should keep the one-time behaviour",
                factory instanceof ReloadableSSLSocketFactory);
        factory.createSocket().close();
    }

    @Test
    public void testRotatedTruststoreFileIsPickedUp() throws Exception {
        File store = File.createTempFile("truststore_", ".p12");
        store.deleteOnExit();
        writeTruststore(store, certificatePath("ca.crt"));
        long modified = System.currentTimeMillis();
        Assert.assertTrue(store.setLastModified(modified));

        ReloadableSSLSocketFactory factory = (ReloadableSSLSocketFactory) SSLUtil.createSSLFactory(store.getAbsolutePath(), null, null, null, null);
        SSLSocketFactory delegate = factory.currentDelegate();

        // rotate the CA: replace the truststore content and move the modification time forward
        writeTruststore(store, certificatePath("hydra.crt"));
        Assert.assertTrue(store.setLastModified(modified + 10_000));

        factory.createSocket().close();
        Assert.assertNotSame("A rotated truststore file should result in a new delegate", delegate, factory.currentDelegate());
    }

    private static Supplier<SSLSocketFactory> countingSupplier(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return newDelegateFactory();
        };
    }

    private static SSLSocketFactory newDelegateFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create delegate factory", e);
        }
    }

    private static File createTempTruststoreFile() throws Exception {
        File file = File.createTempFile("truststore_", ".p12");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "placeholder");
        return file;
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
}
