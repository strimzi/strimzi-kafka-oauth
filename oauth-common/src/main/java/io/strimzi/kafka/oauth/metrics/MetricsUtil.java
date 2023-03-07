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

/**
 * Static utility methods used for metrics
 */
public class MetricsUtil {

    /**
     * Get the attributes for the sensor key using passed info.
     *
     * @param contextId a contextId
     * @param mechanism a SASL authentication mechanism used
     * @param uri a URI associated with the sensor
     * @param kind a 'kind' for the sensor
     *
     * @return The map of attributes for the SensorKey
     */
    public static Map<String, String> getSensorKeyAttrs(String contextId, String mechanism, URI uri, String kind) {
        Map<String, String> attrs = getSensorKeyAttrs(contextId, uri, kind);
        attrs.put("mechanism", mechanism);
        return attrs;
    }

    /**
     * Get the attributes for the sensor key using passed info
     *
     * @param contextId a contextId
     * @param uri a URI associated with the sensor
     * @param kind a 'kind' for the sensor
     *
     * @return The map of attributes for the SensorKey
     */
    public static Map<String, String> getSensorKeyAttrs(String contextId, URI uri, String kind) {
        HashMap<String, String> attrs = new LinkedHashMap<>();
        attrs.put("context", contextId);
        attrs.put("kind", kind);
        attrs.put("host", hostAttr(uri));
        attrs.put("path", pathAttr(uri));
        return attrs;
    }

    /**
     * Add http success attributes to the passed map of attributes
     *
     * @param attrs A target attributes map
     * @return The passed attributes map
     */
    public static Map<String, String> addHttpSuccessAttrs(Map<String, String> attrs) {
        attrs.put("outcome", "success");
        attrs.put("status", "200");
        return attrs;
    }

    /**
     * Add http error attributes to the passed map of attributes
     *
     * @param attrs A target attributes map
     * @param ex The exception that occurred
     * @return The passed attributes map
     */
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

    /**
     * Resolve the raw path of the passed url
     *
     * @param uri a URI object
     * @return The path component of the URI
     */
    public static String pathAttr(URI uri) {
        if (uri == null) {
            return "";
        }
        return uri.getRawPath();
    }

    /**
     * Extract the hostname:port from the passed url
     * For 'http' protocol use port 80 as the default.
     * For 'https' protocol use port 443 as the default.
     *
     * @param uri the URI object
     * @return The hostname:port
     */
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
