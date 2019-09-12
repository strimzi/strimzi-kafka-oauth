/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static io.strimzi.kafka.oauth.common.IOUtil.copy;


public class HttpUtil {

    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    public static <T> T get(URI uri, String authorization, Class<T> responseType) throws IOException {
        return postOrGet(uri, null, null, authorization, null, null, responseType);
    }

    public static <T> T get(URI uri, SSLSocketFactory socketFactory, String authorization, Class<T> responseType) throws IOException {
        return postOrGet(uri, socketFactory, null, authorization, null, null, responseType);
    }

    public static <T> T get(URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, Class<T> responseType) throws IOException {
        return postOrGet(uri, socketFactory, hostnameVerifier, authorization, null, null, responseType);
    }

    public static <T> T post(URI uri, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return postOrGet(uri, null, null, authorization, contentType, body, responseType);
    }

    public static <T> T post(URI uri, SSLSocketFactory socketFactory, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return postOrGet(uri, socketFactory, null, authorization, contentType, body, responseType);
    }

    public static <T> T post(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return postOrGet(uri, socketFactory, verifier, authorization, contentType, body, responseType);
    }

    public static <T> T postOrGet(URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        HttpURLConnection con;
        try {
            con = (HttpURLConnection) uri.toURL().openConnection();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed token endpoint url: " + uri);
        }

        if (con instanceof HttpsURLConnection) {
            HttpsURLConnection scon = (HttpsURLConnection) con;
            if (socketFactory != null) {
                scon.setSSLSocketFactory(socketFactory);
            }
            if (hostnameVerifier != null) {
                scon.setHostnameVerifier(hostnameVerifier);
            }
        } else if (socketFactory != null) {
            log.warn("SSL socket factory set but url scheme not https ({})", uri);
        }

        con.setUseCaches(false);
        if (body != null) {
            con.setDoOutput(true);
        }

        con.setRequestMethod(body != null ? "POST" : "GET");
        if (authorization != null) {
            con.setRequestProperty("Authorization", authorization);
        }
        con.setRequestProperty("Accept", "application/json");
        if (body != null && body.length() > 0) {
            if (contentType == null) {
                throw new IllegalArgumentException("contentType must be set when body is not null");
            }
            con.setRequestProperty("Content-Type", contentType);
        }

        try {
            con.connect();
        } catch (ConnectException e) {
            throw new IOException("Failed to connect to: " + uri, e);
        }

        if (body != null && body.length() > 0) {
            OutputStream out = con.getOutputStream();
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.close();
        }

        int code = con.getResponseCode();
        if (code != 200) {
            InputStream err = con.getErrorStream();
            if (err != null) {
                ByteArrayOutputStream errbuf = new ByteArrayOutputStream(4096);
                try {
                    copy(err, errbuf);
                } catch (Exception e) {
                    log.warn("[IGNORED] Failed to read response body", e);
                }

                throw new RuntimeException("Request failed with status " + code + ": " + errbuf.toString(StandardCharsets.UTF_8.name()));
            } else {
                throw new RuntimeException("Request failed with status " + code + " " + con.getResponseMessage());
            }
        }
        return JSONUtil.readJSON(con.getInputStream(), responseType);
    }
}
