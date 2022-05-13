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

public class ClientAuthenticationSensorKeyProducer implements SensorKeyProducer {

    private final String contextId;
    private final URI uri;

    public ClientAuthenticationSensorKeyProducer(String contextId, URI uri) {
        if (contextId == null) {
            throw new IllegalArgumentException("contextId == null");
        }
        this.contextId = contextId;
        this.uri = uri;
    }

    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "success");
        return SensorKey.of("authentication_requests", attrs);
    }

    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("authentication_requests", attrs);
    }
}
