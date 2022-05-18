/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

/**
 * An interface representing a SensorKey factory.
 *
 * Different locations in the Strimzi OAuth library create differently named sensors with different attributes.
 * Sensors always come in pairs - one for tracking the successful requests, and one for tracking the failed requests.
 */
public interface SensorKeyProducer {

    /**
     * Generate a SensorKey for the sensor representing the successful requests
     *
     * @return A SensorKey
     */
    SensorKey successKey();

    /**
     * Generate a SensorKey for the sensor representing the failed requests
     *
     * @param e The Throwable object to go with the failure
     * @return A SensorKey
     */
    SensorKey errorKey(Throwable e);
}
