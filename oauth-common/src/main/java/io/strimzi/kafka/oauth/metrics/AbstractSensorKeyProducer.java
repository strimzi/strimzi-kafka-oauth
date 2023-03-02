/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.net.URI;

/**
 * A SensorKeyProducer with logic shared by all SensorKeyProducers
 */
public abstract class AbstractSensorKeyProducer implements SensorKeyProducer {

    protected final String contextId;
    protected final URI uri;

    /**
     * The constructor, that requires contexId, and an uri on the authorization server associated with the sensor keys.
     *
     * @param contextId A configId or some other label that identifies the specific configuration or environment
     * @param uri The uri on the authorization server the metrics are associated with
     */
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
