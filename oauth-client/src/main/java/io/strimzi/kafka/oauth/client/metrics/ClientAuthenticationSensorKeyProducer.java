/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client.metrics;

import io.strimzi.kafka.oauth.metrics.MetricsUtil;
import io.strimzi.kafka.oauth.metrics.SensorKey;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;

import java.net.URI;
import java.util.Map;

/**
 * A {@link SensorKeyProducer} used for client authentication metrics
 */
public class ClientAuthenticationSensorKeyProducer implements SensorKeyProducer {

    private final String contextId;
    private final URI uri;

    /**
     * Create a new instance
     *
     * @param contextId Context id (e.g. a config id or a label)
     * @param uri A token endpoint url
     */
    public ClientAuthenticationSensorKeyProducer(String contextId, URI uri) {
        if (contextId == null) {
            throw new IllegalArgumentException("contextId == null");
        }
        this.contextId = contextId;
        this.uri = uri;
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about successful client authentication requests
     *
     * @return A sensor key
     */
    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "success");
        return SensorKey.of("authentication_requests", attrs);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about failed client authentication requests
     *
     * @param e The Throwable object to go with the failure
     * @return A sensor key
     */
    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("authentication_requests", attrs);
    }
}
