/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;
import java.util.Map;

/**
 * A {@link SensorKeyProducer} used for token validation metrics for a listener using an introspection endpoint
 */
public class IntrospectValidationSensorKeyProducer extends AbstractSensorKeyProducer {

    private final String saslMechanism;

    /**
     * Create a new instance
     *
     * @param contextId Context id (e.g. a config id or a label)
     * @param saslMechanism a mechanism through which the validator is used (e.g. OAUTHBEARER, PLAIN)
     * @param uri An introspection endpoint url
     */
    public IntrospectValidationSensorKeyProducer(String contextId, String saslMechanism, URI uri) {
        super(contextId, uri);

        if (saslMechanism == null) {
            throw new IllegalArgumentException("saslMechanism == null");
        }
        this.saslMechanism = saslMechanism;
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about successful token validation requests
     *
     * @return A sensor key
     */
    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, saslMechanism, uri, "introspect");
        attrs.put("outcome", "success");
        return SensorKey.of("validation_requests", attrs);
    }

    /**
     * Generate a <code>SensorKey</code> for metrics about failed token validation requests
     *
     * @param e The Throwable object to go with the failure
     * @return A sensor key
     */
    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, saslMechanism, uri, "introspect");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("validation_requests", attrs);
    }
}
