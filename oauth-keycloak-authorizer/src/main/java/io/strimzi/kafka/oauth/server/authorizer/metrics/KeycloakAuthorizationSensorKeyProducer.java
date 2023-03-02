/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer.metrics;

import io.strimzi.kafka.oauth.metrics.AbstractSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.MetricsUtil;
import io.strimzi.kafka.oauth.metrics.SensorKey;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;

import java.net.URI;
import java.util.Map;

/**
 * A {@link SensorKeyProducer} used for authorization metrics
 */
public class KeycloakAuthorizationSensorKeyProducer extends AbstractSensorKeyProducer {

    /**
     * Create a new instance
     *
     * @param contextId Context id (e.g. a config id or a label)
     * @param uri A token endpoint url
     */
    public KeycloakAuthorizationSensorKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about successful authorizations
     *
     * @return A sensor key
     */
    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "success");
        return SensorKey.of("authorization_requests", attrs);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about failed authorizations
     *
     * @param e The Throwable object to go with the failure
     * @return A sensor key
     */
    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("authorization_requests", attrs);
    }
}
