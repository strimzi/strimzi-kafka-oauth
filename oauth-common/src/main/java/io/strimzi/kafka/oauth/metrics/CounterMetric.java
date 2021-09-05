/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class CounterMetric {

    private final String name;
    private final String description;
    private final AtomicLong value = new AtomicLong();

    public CounterMetric(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getValueType() {
        return Long.class.getName();
    }

    public long getValue() {
        return value.get();
    }

    public void setValue(long val) {
        value.set(val);
    }

    public long increment() {
        return value.incrementAndGet();
    }

    public long incrementBy(long val) {
        return value.addAndGet(val);
    }
}
