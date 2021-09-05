/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client.metrics;

import io.strimzi.kafka.oauth.metrics.JmxMetrics;
import io.strimzi.kafka.oauth.metrics.MetricKey;
import io.strimzi.kafka.oauth.metrics.MetricKeyProducer;

import java.net.URI;
import java.util.Map;

public class ClientAuthenticationMetricKeyProducer implements MetricKeyProducer {

    private final String contextId;
    private final URI uri;

    public ClientAuthenticationMetricKeyProducer(String contextId, URI uri) {
        if (contextId == null) {
            throw new IllegalArgumentException("contextId == null");
        }
        this.contextId = contextId;
        this.uri = uri;
    }

    @Override
    public MetricKey successKey() {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "success");
        return MetricKey.of("authentication_requests", attrs);
    }

    @Override
    public MetricKey errorKey(Throwable e) {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "client-auth");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return MetricKey.of("authentication_requests", attrs);
    }
}
