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
 * A {@link SensorKeyProducer} used for token endpoint HTTP metrics for Keycloak grants requests performed by
 * {@link io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer}
 */
public class GrantsHttpSensorKeyProducer extends AbstractSensorKeyProducer {

    /**
     * Create a new instance
     *
     * @param contextId Context id (e.g. a config id or a label)
     * @param uri A token endpoint url
     */
    public GrantsHttpSensorKeyProducer(String contextId, URI uri) {
        super(contextId, uri);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about successful HTTP requests
     *
     * @return A sensor key
     */
    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        MetricsUtil.addHttpSuccessAttrs(attrs);
        return SensorKey.of("http_requests", attrs);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about failed HTTP requests
     *
     * @param e The Throwable object to go with the failure
     * @return A sensor key
     */
    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, uri, "keycloak-authorization");
        return SensorKey.of("http_requests", MetricsUtil.addHttpErrorAttrs(attrs, e));
    }
}
