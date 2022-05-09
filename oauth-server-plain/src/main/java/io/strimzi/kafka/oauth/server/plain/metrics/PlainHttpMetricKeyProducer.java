/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain.metrics;

import io.strimzi.kafka.oauth.metrics.AbstractMetricKeyProducer;
import io.strimzi.kafka.oauth.metrics.JmxMetrics;
import io.strimzi.kafka.oauth.metrics.MetricKey;

import java.net.URI;
import java.util.Map;

public class PlainHttpMetricKeyProducer extends AbstractMetricKeyProducer {

    public PlainHttpMetricKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public MetricKey successKey() {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "plain");
        JmxMetrics.addHttpSuccessAttrs(attrs);
        return MetricKey.of("http_requests", attrs);
    }

    @Override
    public MetricKey errorKey(Throwable e) {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "plain");
        return MetricKey.of("http_requests", JmxMetrics.addHttpErrorAttrs(attrs, e));
    }
}
