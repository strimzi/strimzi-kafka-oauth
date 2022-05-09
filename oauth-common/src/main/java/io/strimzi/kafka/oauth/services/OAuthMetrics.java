/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.metrics.MetricKey;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.metrics.stats.Avg;
import org.apache.kafka.common.metrics.stats.CumulativeCount;
import org.apache.kafka.common.metrics.stats.CumulativeSum;
import org.apache.kafka.common.metrics.stats.Max;
import org.apache.kafka.common.metrics.stats.Min;
import org.apache.kafka.common.utils.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_RECORDING_LEVEL_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG;

/**
 * The singleton for handling a cache of all the Sensors to prevent unnecessary redundant re-registrations.
 * There is a one-to-one mapping between a metric key and a Sensor, and one-to-one mapping between a sensor and an MBean name.
 *
 * MBeans are registered by Metrics object as requested.
 */
public class OAuthMetrics {

    private static final Logger log = LoggerFactory.getLogger(OAuthMetrics.class);

    private final Map<String, ?> configMap;
    private final Config config;
    private final Metrics metrics;

    private final Map<MetricKey, Sensor> sensorMap = new ConcurrentHashMap<>();

    OAuthMetrics(Map<String, ?> configMap) {
        this.configMap = configMap;
        this.config = new Config(configMap);
        this.metrics = initKafkaMetrics();
    }

    public void addTime(MetricKey key, long time) {
        Sensor registeredSensor = sensorMap.computeIfAbsent(key, k -> {
            Sensor sensor = metrics.sensor(key.getMBeanName());
            addMetricsToSensor(metrics, sensor, key);
            return sensor;
        });

        registeredSensor.record(time);
    }

    private Metrics initKafkaMetrics() {
        List<MetricsReporter> reporters = initReporters();
        KafkaMetricsContext ctx = createKafkaMetricsContext();

        MetricConfig metricConfig = getMetricConfig();

        return createMetrics(metricConfig, ctx, reporters);
    }

    private List<MetricsReporter> initReporters() {
        AbstractConfig kafkaConfig = initKafkaConfig();
        List<MetricsReporter> reporters = kafkaConfig.getConfiguredInstances(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
                MetricsReporter.class);

        JmxReporter reporter = new JmxReporter();
        reporter.configure(configMap);

        reporters.add(reporter);
        return reporters;
    }

    private AbstractConfig initKafkaConfig() {
        ConfigDef configDef = new ConfigDef()
                .define(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
                        ConfigDef.Type.LIST,
                        Collections.emptyList(),
                        new ConfigDef.NonNullValidator(),
                        ConfigDef.Importance.LOW,
                        CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC);

        return new AbstractConfig(configDef, configMap);
    }

    private KafkaMetricsContext createKafkaMetricsContext() {
        HashMap<String, String> contextLabels = new HashMap<>();

        // Broker side init code (for reference)
        // See: kafka.server.Server.scala#createKafkaMetricsContext

        // We don't know how to get clusterId here
        //contextLabels.put("kafka.cluster.id", getClusterId());

        //if (config.usesSelfManagedQuorum) {
        //    contextLabels.put("kafka.node.id", nodeId)
        //} else {
        //    contextLabels.put("kafka.broker.id", brokerId)
        //}

        String processRoles = config.getValue("process.roles");
        if (processRoles == null || "[]".equals(processRoles)) {
            String brokerId = config.getValue("broker.id");
            if (brokerId != null) {
                contextLabels.put("kafka.broker.id", brokerId);
            }
        } else {
            String nodeId = config.getValue("node.id");
            if (nodeId != null) {
                contextLabels.put("kafka.node.id", nodeId);
            }
        }

        if (config.getValue(CLIENT_ID_CONFIG) != null) {
            String clientId = config.getValue(CLIENT_ID_CONFIG);
            if (clientId != null) {
                contextLabels.put(CLIENT_ID_CONFIG, clientId);
            }
        }

        addConfiguredContextLabels(contextLabels);
        return new KafkaMetricsContext("strimzi.oauth", contextLabels);
    }

    private void addConfiguredContextLabels(HashMap<String, String> contextLabels) {
        String prefix = "metrics.context.";
        for (Map.Entry<String, ?> entry: configMap.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                contextLabels.put(entry.getKey().substring(prefix.length()), String.valueOf(entry.getValue()));
            }
        }
    }

    private MetricConfig getMetricConfig() {

        int numSamples = config.getValueAsInt(METRICS_NUM_SAMPLES_CONFIG, 2);
        Sensor.RecordingLevel recordingLevel = Sensor.RecordingLevel.forName(config.getValue(METRICS_RECORDING_LEVEL_CONFIG, "INFO"));
        long sampleWindowMs = config.getValueAsLong(METRICS_SAMPLE_WINDOW_MS_CONFIG, 30_000L);
        return new MetricConfig()
            .samples(numSamples)
            .recordLevel(recordingLevel)
            .timeWindow(sampleWindowMs, TimeUnit.MILLISECONDS);
    }

    private Metrics createMetrics(MetricConfig metricConfig, KafkaMetricsContext ctx, List<MetricsReporter> reporters) {
        log.debug("Creating Metrics: \n    metricConfig: {samples: " + metricConfig.samples() + ", recordingLevel: " +
                metricConfig.recordLevel() + ", timeWindowMs: " + metricConfig.timeWindowMs() + "}\n    kafkaMetricsContext: " +
                ctx.contextLabels() + "\n    reporters: " + reporters);
        return new Metrics(metricConfig, reporters, Time.SYSTEM, ctx);
    }

    private void addMetricsToSensor(Metrics metrics, Sensor sensor, MetricKey key) {
        MetricName metricName = new MetricName("count", key.getName(), "Total request count", key.getAttrs());
        sensor.add(metricName, new CumulativeCount());

        metricName = new MetricName("timeTotal", key.getName(), "Total request time in ms", key.getAttrs());
        sensor.add(metricName, new CumulativeSum());

        metricName = new MetricName("timeAvg", key.getName(), "Average request time in ms", key.getAttrs());
        sensor.add(metricName, new Avg());

        metricName = new MetricName("timeMax", key.getName(), "Max request time in ms", key.getAttrs());
        sensor.add(metricName, new Max());

        metricName = metrics.metricName("timeMin", key.getName(), "Min request time in ms", key.getAttrs());
        sensor.add(metricName, new Min());
    }
}
