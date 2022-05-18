/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain.metrics;

import io.strimzi.kafka.oauth.metrics.AbstractSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.MetricsUtil;
import io.strimzi.kafka.oauth.metrics.SensorKey;

import java.net.URI;
import java.util.Map;

public class PlainHttpSensorKeyProducer extends AbstractSensorKeyProducer {

    public PlainHttpSensorKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "plain");
        MetricsUtil.addHttpSuccessAttrs(attrs);
        return SensorKey.of("http_requests", attrs);
    }

    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "plain");
        return SensorKey.of("http_requests", MetricsUtil.addHttpErrorAttrs(attrs, e));
    }
}
