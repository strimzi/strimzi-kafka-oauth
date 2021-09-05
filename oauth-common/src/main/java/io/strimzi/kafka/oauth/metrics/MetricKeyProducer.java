/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

public interface MetricKeyProducer {

    MetricKey successKey();

    MetricKey errorKey(Throwable e);
}
