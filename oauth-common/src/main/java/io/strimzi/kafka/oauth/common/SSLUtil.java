/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

/**
 * A class containing helper methods that deal with SSL / TLS
 */
public class SSLUtil {

    /**
     * Create a new SSL Factory from given configuration arguments
     *
     * @param truststore A path to truststore file (used by default)
     * @param truststoreData A PEM string containing a chain of X.509 certificates. Used if <code>type</code> is set to <code>pem</code>
     * @param password A password for the truststore file (optional)
     * @param type A truststore type (e.g. PKCS12, JKS, or PEM) (optional)
     * @param rnd A random number generator to use (optional)
     * @return A new SSLSocketFactory instance
     */
    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION",
            justification = "Avoid enumerating all checked exceptions in try-with-resources")
    public static SSLSocketFactory createSSLFactory(String truststore, String truststoreData, String password, String type, String rnd) {
        KeyStore store;

        if (type != null && "pem".equals(type.toLowerCase(Locale.ENGLISH))) {
            if (truststoreData != null) {
                try (ByteArrayInputStream is = new ByteArrayInputStream(truststoreData.getBytes(StandardCharsets.UTF_8))) {
                    store = loadPEMCertificates(is);
                } catch (Exception e) {
                    throw new ConfigException("Failed to load PEM truststore: " + truststore, e);
                }
            } else if (truststore != null) {
                try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(truststore))) {
                    store = loadPEMCertificates(is);
                } catch (Exception e) {
                    throw new ConfigException("Failed to load PEM truststore: " + truststore, e);
                }
            } else {
                return null;
            }
        } else if (truststore != null) {
            try (FileInputStream is = new FileInputStream(truststore)) {
                store = KeyStore.getInstance(type != null ? type : KeyStore.getDefaultType());
                store.load(is, password != null ? password.toCharArray() : new char[0]);
            } catch (Exception e) {
                throw new ConfigException("Failed to load truststore: " + truststore, e);
            }
        } else {
            return null;
        }

        X509TrustManager tm;
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);

            tm = getTrustManager(tmf);
        } catch (Exception e) {
            throw new ConfigException("Failed to initialise truststore: " + truststore, e);
        }

        SecureRandom random = null;
        if (rnd != null) {
            try {
                random = SecureRandom.getInstance(rnd);
            } catch (Exception e) {
                throw new ConfigException("Failed to initialise secure random: " + rnd, e);
            }
        }

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {tm}, random);
        } catch (Exception e) {
            throw new ConfigException("Failed to initialise ssl context", e);
        }

        return sslContext.getSocketFactory();
    }

    private static X509TrustManager getTrustManager(TrustManagerFactory tmf) {
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509TrustManager on default factory");
    }

    /**
     * Create a new instance of HostnameVerifier that accepts any hostname
     *
     * @return A new HostnameVerifier instance
     */
    public static HostnameVerifier createAnyHostHostnameVerifier() {
        return (hostname, session) -> true;
    }

    private static KeyStore loadPEMCertificates(InputStream is) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);

        CertificateFactory certFactory = CertificateFactory.getInstance("X509");

        while (is.available() > 0) {
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(is);
            String alias = cert.getSubjectX500Principal().getName() + "_" + cert.getSerialNumber().toString(16);
            store.setCertificateEntry(alias, cert);
        }

        return store;
    }
}
