/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

public class SSLUtil {

    public static SSLSocketFactory createSSLFactory(String truststore, String password, String type, String rnd) {

        if (truststore == null) {
            return null;
        }

        KeyStore store;
        try (FileInputStream is = new FileInputStream(truststore)) {
            store = KeyStore.getInstance(type != null ? type : KeyStore.getDefaultType());
            store.load(is, password.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load truststore: " + truststore, e);
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
            sslContext.init(null, new TrustManager[] { tm }, random);
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
