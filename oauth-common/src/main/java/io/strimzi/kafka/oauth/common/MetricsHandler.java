/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

public interface MetricsHandler {

    void addSuccessRequestTime(long millis);

    void addErrorRequestTime(Throwable e, long millis);
}
