/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer.metrics;

import io.strimzi.kafka.oauth.metrics.AbstractSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.MetricsUtil;
import io.strimzi.kafka.oauth.metrics.SensorKey;

import java.net.URI;
import java.util.Map;

public class KeycloakAuthorizationSensorKeyProducer extends AbstractSensorKeyProducer {

    public KeycloakAuthorizationSensorKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "success");
        return SensorKey.of("authorization_requests", attrs);
    }

    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("authorization_requests", attrs);
    }
}
