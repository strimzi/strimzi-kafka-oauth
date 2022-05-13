/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;
import java.util.Map;

public class JwksValidationSensorKeyProducer extends AbstractSensorKeyProducer {

    private final String saslMechanism;

    public JwksValidationSensorKeyProducer(String contextId, String saslMechanism, URI uri) {
        super(contextId, uri);

        if (saslMechanism == null) {
            throw new IllegalArgumentException("saslMechanism == null");
        }
        this.saslMechanism = saslMechanism;
    }

    @Override
    public SensorKey successKey() {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, saslMechanism, uri, "jwks");
        attrs.put("outcome", "success");
        return SensorKey.of("validation_requests", attrs);
    }

    @Override
    public SensorKey errorKey(Throwable e) {
        Map<String, String> attrs = MetricsUtil.getSensorKeyAttrs(contextId, saslMechanism, uri, "jwks");
        attrs.put("outcome", "error");
        attrs.put("error_type", "other");
        return SensorKey.of("validation_requests", attrs);
    }
}
