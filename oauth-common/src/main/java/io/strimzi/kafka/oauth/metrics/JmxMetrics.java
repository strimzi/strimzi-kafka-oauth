/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import io.strimzi.kafka.oauth.common.HttpException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JmxMetrics {

    public static Map<String, String> getMetricKeyAttrs(String contextId, String mechanism, URI uri, String kind) {
        Map<String, String> attrs = getMetricKeyAttrs(contextId, uri, kind);
        attrs.put("mechanism", mechanism);
        return attrs;
    }

    public static Map<String, String> getMetricKeyAttrs(String contextId, URI uri, String kind) {
        HashMap<String, String> attrs = new LinkedHashMap<>();
        attrs.put("context", contextId);
        attrs.put("kind", kind);
        attrs.put("host", hostAttr(uri));
        attrs.put("path", pathAttr(uri));
        return attrs;
    }

    public static Map<String, String> addHttpErrorAttrs(Map<String, String> attrs, Throwable ex) {
        String errorType = "other";
        String status = null;

        if (ex instanceof HttpException) {
            HttpException e = (HttpException) ex;
            status = String.valueOf(e.getStatus());
            errorType = "http";
        } else if (ex instanceof SSLException) {
            errorType = "tls";
        } else if (ex instanceof IOException) {
            if (ex.getCause() instanceof ConnectException) {
                errorType = "connect";
            }
        }

        attrs.put("outcome", "error");
        attrs.put("error_type", errorType);
        if (status != null) {
            attrs.put("status", status);
        }
        return attrs;
    }

    public static String pathAttr(URI uri) {
        if (uri == null) {
            return "";
        }
        return uri.getRawPath();
    }

    public static String hostAttr(URI uri) {
        if (uri == null) {
            return "";
        }
        int port = uri.getPort();
        if (port == -1) {
            String scheme = uri.getScheme();
            if (scheme.equals("http")) {
                port = 80;
            } else if (scheme.equals("https")) {
                port = 443;
            }
        }

        return uri.getHost() + (port != -1 ? ":" + port : "");
    }
}
