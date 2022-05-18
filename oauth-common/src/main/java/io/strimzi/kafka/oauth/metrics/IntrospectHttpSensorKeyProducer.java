/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;
import java.util.Map;

public class IntrospectHttpSensorKeyProducer extends AbstractSensorKeyProducer {

    public IntrospectHttpSensorKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "introspect");
        MetricsUtil.addHttpSuccessAttrs(attrs);
        return SensorKey.of("http_requests", attrs);
    }

    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "introspect");
        return SensorKey.of("http_requests", MetricsUtil.addHttpErrorAttrs(attrs, e));
    }
}
