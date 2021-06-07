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
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SSLUtil {

    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION",
            justification = "Avoid enumerating all checked exceptions in try-with-resources")
    public static SSLSocketFactory createSSLFactory(String truststore, String password, String type, String rnd) {

        if (truststore == null) {
            return null;
        }

        KeyStore store;

        if ("PEM".equals(type)) {
            try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(truststore))) {
                store = KeyStore.getInstance("PKCS12");
                store.load(null, null);

                CertificateFactory certFactory = CertificateFactory.getInstance("X509");

                while (is.available() > 0) {
                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(is);
                    String alias = cert.getSubjectX500Principal().getName() + "_" + cert.getSerialNumber().toString(16);
                    store.setCertificateEntry(alias, cert);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load PEM truststore: " + truststore, e);
            }
        } else {
            try (FileInputStream is = new FileInputStream(truststore)) {
                store = KeyStore.getInstance(type != null ? type : KeyStore.getDefaultType());
                store.load(is, password.toCharArray());
            } catch (Exception e) {
                throw new RuntimeException("Failed to load truststore: " + truststore, e);
            }
        }

        X509TrustManager tm;
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);

            tm = getTrustManager(tmf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise truststore: " + truststore, e);
        }

        SecureRandom random = null;
        if (rnd != null) {
            try {
                random = SecureRandom.getInstance(rnd);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialise secure random: " + rnd, e);
            }
        }

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {tm}, random);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise ssl context", e);
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

    public static HostnameVerifier createAnyHostHostnameVerifier() {
        return (hostname, session) -> true;
    }
}
