/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer.metrics;

import io.strimzi.kafka.oauth.metrics.AbstractMetricKeyProducer;
import io.strimzi.kafka.oauth.metrics.JmxMetrics;
import io.strimzi.kafka.oauth.metrics.MetricKey;

import java.net.URI;
import java.util.Map;

public class KeycloakAuthorizationMetricKeyProducer extends AbstractMetricKeyProducer {

    public KeycloakAuthorizationMetricKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public MetricKey successKey() {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "success");
        return MetricKey.of("authorization_requests", attrs);
    }

    @Override
    public MetricKey errorKey(Throwable e) {
        Map<String, String> attrs = JmxMetrics.getMetricKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return MetricKey.of("authorization_requests", attrs);
    }
}
