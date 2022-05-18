/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;

public abstract class AbstractSensorKeyProducer implements SensorKeyProducer {

    protected final String contextId;
    protected final URI uri;

    public AbstractSensorKeyProducer(String contextId, URI uri) {

        if (contextId == null) {
            throw new IllegalArgumentException("contextId == null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri == null");
        }
        this.contextId = contextId;
        this.uri = uri;
    }
}
