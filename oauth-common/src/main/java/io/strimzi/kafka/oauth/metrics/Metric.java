/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

public class Metric {

    private final MetricsMBean mbean;

    private final CounterMetric counter;
    private final CounterMetric timeTotal;

    public Metric(String mbeanName, String description) {
        counter = new CounterMetric("count",
                "Request count");

        timeTotal = new CounterMetric("timeTotal", "Total time spent in requests (in milliseconds");
        this.mbean = new MetricsMBean(mbeanName, description, JmxMetrics.toMetricsMap(counter, timeTotal));
    }

    public MetricsMBean getMbean() {
        return mbean;
    }

    public void addTime(long time) {
        synchronized (mbean.getLock()) {
            counter.increment();
            timeTotal.incrementBy(time);
        }
    }
}
