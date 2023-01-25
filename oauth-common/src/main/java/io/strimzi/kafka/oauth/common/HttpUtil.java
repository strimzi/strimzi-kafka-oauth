/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.oauth.common.ConfigUtil.getConnectTimeout;
import static io.strimzi.kafka.oauth.common.ConfigUtil.getReadTimeout;
import static io.strimzi.kafka.oauth.common.IOUtil.copy;

/**
 * A helper class that performs all network calls using java.net.HttpURLConnection.
 * <p>
 * If application uses many concurrent threads initiating many Kafka sessions in parallel, consider setting
 * 'http.maxConnections' system property to value closer to the number of parallel sessions.
 * <p>
 * This value controls the size of internal connection pool per destination in JDK's java.net.HttpURLConnection implementation.
 * <p>
 * See: https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
 * <p>
 * By default the connect timeout and read timeout are set to 60 seconds. Use system properties <em>oauth.connect.timeout.seconds</em>
 * and <em>oauth.read.timeout.seconds</em>, or corresponding env variables to set custom timeouts in seconds.
 */
public class HttpUtil {

    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    static final int DEFAULT_CONNECT_TIMEOUT = getConnectTimeout(new Config());
    static final int DEFAULT_READ_TIMEOUT = getReadTimeout(new Config());

    public static <T> T doWithRetries(int retries, long retryPauseMillis, IOTask<T> task) throws ExecutionException {

        if (retries < 0) {
            throw new IllegalArgumentException("retries can't be negative");
        }

        retryPauseMillis = retryPauseMillis < 0 ? 0 : retryPauseMillis;

        Exception exception;
        int i = 0;
        do {
            i++;

            if (i > 1 && retryPauseMillis > 0) {
                log.debug("Pausing before retrying failed action (for {}ms)", retryPauseMillis);
                try {
                    Thread.sleep(retryPauseMillis);
                } catch (InterruptedException e) {
                    throw new ExecutionException("Interrupted while pausing", e);
                }
            }

            try {
                if (i > 1) {
                    log.debug("Request attempt no. " + i);
                }
                return task.run();
            } catch (Exception e) {
                exception = e;
                log.info("Action failed on try no. " + i, e);
            }
        } while (i <= retries);

        String msg = "Action failed after " + i + " tries";
        log.debug(msg);
        throw new ExecutionException(msg, exception);
    }

    public static <T> T get(URI uri, String authorization, Class<T> responseType) throws IOException {
        return request(uri, null, null, authorization, null, null, responseType);
    }

    public static <T> T get(URI uri, SSLSocketFactory socketFactory, String authorization, Class<T> responseType) throws IOException {
        return request(uri, socketFactory, null, authorization, null, null, responseType);
    }

    public static <T> T get(URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, Class<T> responseType) throws IOException {
        return request(uri, socketFactory, hostnameVerifier, authorization, null, null, responseType);
    }

    public static <T> T get(URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, Class<T> responseType, int connectTimeout, int readTimeout) throws IOException {
        return request(uri, "GET", socketFactory, hostnameVerifier, authorization, null, null, responseType, connectTimeout, readTimeout);
    }

    public static <T> T post(URI uri, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return request(uri, null, null, authorization, contentType, body, responseType);
    }

    public static <T> T post(URI uri, SSLSocketFactory socketFactory, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return request(uri, socketFactory, null, authorization, contentType, body, responseType);
    }

    public static <T> T post(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return request(uri, socketFactory, verifier, authorization, contentType, body, responseType);
    }

    public static <T> T post(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, String contentType, String body, Class<T> responseType, int connectTimeout, int readTimeout) throws IOException {
        return request(uri, "POST", socketFactory, verifier, authorization, contentType, body, responseType, connectTimeout, readTimeout);
    }

    public static void put(URI uri, String authorization, String contentType, String body) throws IOException {
        request(uri, null, null, authorization, contentType, body, null);
    }

    public static void put(URI uri, SSLSocketFactory socketFactory, String authorization, String contentType, String body) throws IOException {
        request(uri, socketFactory, null, authorization, contentType, body, null);
    }

    public static void put(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, String contentType, String body) throws IOException {
        request(uri, socketFactory, verifier, authorization, contentType, body, null);
    }

    public static void put(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, String contentType, String body, int connectTimeout, int readTimeout) throws IOException {
        request(uri, "PUT", socketFactory, verifier, authorization, contentType, body, null, connectTimeout, readTimeout);
    }

    public static void delete(URI uri, String authorization) throws IOException {
        request(uri, "DELETE", null, null, authorization, null, null, null, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static void delete(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization) throws IOException {
        request(uri, "DELETE", socketFactory, verifier, authorization, null, null, null, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static void delete(URI uri, SSLSocketFactory socketFactory, HostnameVerifier verifier, String authorization, int connectTimeout, int readTimeout) throws IOException {
        request(uri, "DELETE", socketFactory, verifier, authorization, null, null, null, connectTimeout, readTimeout);
    }

    public static <T> T request(URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return request(uri, null, socketFactory, hostnameVerifier, authorization, contentType, body, responseType, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static <T> T request(URI uri, String method, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization, String contentType, String body, Class<T> responseType) throws IOException {
        return request(uri, method, socketFactory, hostnameVerifier, authorization, contentType, body, responseType, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    // Suppressed because of Spotbugs Java 11 bug - https://github.com/spotbugs/spotbugs/issues/756
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public static <T> T request(URI uri, String method, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier, String authorization,
                                String contentType, String body, Class<T> responseType, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection con;
        try {
            con = (HttpURLConnection) uri.toURL().openConnection();
        } catch (IOException e) {
            throw new IOException("Failed to initialise connection to: " + uri, e);
        }

        configureTimeouts(con, connectTimeout, readTimeout);

        configureTLS(con, uri, socketFactory, hostnameVerifier);

        con.setUseCaches(false);
        if (body != null) {
            con.setDoOutput(true);
        }

        if (method == null) {
            method = body == null ? "GET" : responseType != null ? "POST" : "PUT";
        }
        con.setRequestMethod(method);
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
            try (OutputStream out = con.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        return handleResponse(con, method, uri, responseType);
    }

    private static void configureTLS(HttpURLConnection con, URI uri, SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier) {
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
    }

    private static void configureTimeouts(HttpURLConnection con, int connectTimeout, int readTimeout) {
        if (connectTimeout <= 0) {
            throw new IllegalArgumentException("connectTimeout <= 0");
        }
        con.setConnectTimeout(connectTimeout * 1000);

        if (readTimeout <= 0) {
            throw new IllegalArgumentException("readTimeout <= 0");
        }
        con.setReadTimeout(readTimeout * 1000);
    }

    // Surpressed because of Spotbugs Java 11 bug - https://github.com/spotbugs/spotbugs/issues/756
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private static <T> T handleResponse(HttpURLConnection con, String method, URI uri, Class<T> responseType) throws IOException {
        int code = con.getResponseCode();
        if (code != 200 && code != 201 && code != 204) {
            InputStream err = con.getErrorStream();
            if (err != null) {
                ByteArrayOutputStream errbuf = new ByteArrayOutputStream(4096);
                try {
                    copy(err, errbuf);
                } catch (Exception e) {
                    log.warn("[IGNORED] Failed to read response body", e);
                }

                throw new HttpException(method, uri, code, errbuf.toString(StandardCharsets.UTF_8.name()));
            } else {
                throw new HttpException(method, uri, code, con.getResponseMessage());
            }
        }

        try (InputStream response = con.getInputStream()) {
            if (responseType == null) {
                response.close();
                return null;
            }
            InputStream is = response;
            if (log.isTraceEnabled()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                IOUtil.copy(response, buffer);
                log.trace("Response body for " + method + " " + uri + ": " + buffer.toString("utf-8"));

                is = new ByteArrayInputStream(buffer.toByteArray());
            }
            return JSONUtil.readJSON(is, responseType);
        }

        // Don't call con.disconnect() in order to allow connection reuse.
        //
        // The connection pool per destination is determined by http.maxConnections system property.
        //
        // See also:
        //   https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html
        //   https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
        //   https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html
    }
}
