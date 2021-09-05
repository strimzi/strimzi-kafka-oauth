/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import io.strimzi.kafka.oauth.metrics.JmxMetrics;
import io.strimzi.kafka.oauth.metrics.Metric;
import io.strimzi.kafka.oauth.metrics.MetricKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * The singleton for handling a cache of all metric counters indexed by metric keys.
 * Metric keys are composed of the basic metric name and a set of attributes that contextualise the metric.
 * There is a one-to-one mapping between a metric key and an MBean name.
 *
 * MBeans are registered lazily when the metric is requested.
 */
public class Metrics {

    private Map<MetricKey, Metric> metricsMap = new ConcurrentHashMap<>();

    public void addTime(MetricKey key, long time) {
        Metric metric = metricsMap.computeIfAbsent(key, k -> {
            Metric m = new Metric(key.getMBeanName(), "description");
            JmxMetrics.INSTANCE.ensureMBean(m.getMbean());
            return m;
        });

        metric.addTime(time);
    }
}
