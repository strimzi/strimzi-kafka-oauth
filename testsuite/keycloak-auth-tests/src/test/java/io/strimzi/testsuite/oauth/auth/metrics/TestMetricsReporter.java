/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth.metrics;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestMetricsReporter implements org.apache.kafka.common.metrics.MetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(TestMetricsReporter.class);

    private static final AtomicInteger COUNT = new AtomicInteger(0);

    @Override
    public void init(List<KafkaMetric> metrics) {
        log.debug("TestMetricsReporter no. " + COUNT.incrementAndGet() + " init : " + metrics.stream().map(KafkaMetric::metricName).collect(Collectors.toList()));
    }

    @Override
    public void metricChange(KafkaMetric metric) {
        log.debug("TestMetricsReporter metricChange - newly registered metric: " + metric.metricName());
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        log.debug("TestMetricsReporter metricRemoval: " + metric.metricName());
    }

    @Override
    public void close() {
        log.debug("TestMetricsReporter close");
    }

    @Override
    public void configure(Map<String, ?> configs) {
        log.debug("TestMetricsReporter configure: " + configs);
    }
}
