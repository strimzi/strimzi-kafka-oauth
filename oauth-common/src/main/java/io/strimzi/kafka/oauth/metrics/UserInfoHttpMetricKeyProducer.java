/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;
import java.util.Map;

public class UserInfoHttpMetricKeyProducer extends AbstractMetricKeyProducer {

    public UserInfoHttpMetricKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public MetricKey successKey() {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "userinfo");
        attrs.put("outcome", "success");
        attrs.put("status", "200");
        return MetricKey.of("http_requests", attrs);
    }

    @Override
    public MetricKey errorKey(Throwable e) {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "userinfo");
        return MetricKey.of("http_requests", JmxMetrics.addHttpErrorAttrs(attrs, e));
    }
}
