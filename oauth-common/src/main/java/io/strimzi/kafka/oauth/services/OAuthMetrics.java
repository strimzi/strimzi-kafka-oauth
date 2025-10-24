/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.metrics.SensorKey;
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
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.metrics.GlobalConfig.STRIMZI_OAUTH_METRIC_REPORTERS;
import static org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_RECORDING_LEVEL_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG;

/**
 * The singleton for handling a cache of all the Sensors to prevent unnecessary redundant re-registrations.
 * There is a one-to-one mapping between a <code>SensorKey</code> and a <code>Sensor</code>, and one-to-one mapping between a <code>Sensor</code> and an <code>MBean</code> name.
 * <p>
 * MBeans are registered as requested by <code>JmxReporter</code> attached to the <code>Metrics</code> object.
 * The <code>JmxReporter</code> either has to be explicitly configured using a config option <code>strimzi.oauth.metric.reporters</code>,
 * or if that config option si not set, a new instance is configured by default.
 * <p>
 * Since OAuth instantiates its own <code>Metrics</code> object it also has to instantiate reporters to attach them to this <code>Metrics</code> object.
 * To prevent double instantiation of <code>MetricReporter</code> objects that require to be singleton, all <code>MetricReporter</code> objects
 * to be integrated with <code>OAuthMetrics</code> have to be separately instantiated.
 * <p>
 * Example 1:
 * <pre>
 *    strimzi.oauth.metric.reporters=org.apache.kafka.common.metrics.JmxReporter,org.some.package.SomeMetricReporter
 * </pre>
 * The above will instantiate and integrate with OAuth metrics the JmxReporter instance, and a SomeMetricReporter instance.
 * <p>
 * Example 2:
 * <pre>
 *    strimzi.oauth.metric.reporters=
 * </pre>
 * The above will not instantiate and integrate any metric reporters with OAuth metrics, not even JmxReporter.
 * <p>
 * Note: On the Kafka broker it is best to use <code>STRIMZI_OAUTH_METRIC_REPORTERS</code> env variable or <code>strimzi.oauth.metric.reporters</code> system property,
 * rather than a `server.properties` global configuration option.
 */
public class OAuthMetrics {

    private static final Logger log = LoggerFactory.getLogger(OAuthMetrics.class);

    private final Map<String, ?> configMap;
    private final Config config;
    private final Metrics metrics;

    private final Map<SensorKey, Sensor> sensorMap = new ConcurrentHashMap<>();

    /**
     * Create a new instance of OAuthMetrics object and initialise the Kafka Metrics, and MetricReporters based on the configuration.
     *
     * @param configMap Configuration properties
     */
    @SuppressWarnings("unchecked")
    OAuthMetrics(Map<String, ?> configMap) {
        this.config = new Config(configMap);

        // Make sure to add the resolved 'strimzi.oauth.metric.reporters' configuration to the config map
        ((Map<String, Object>) configMap).put(STRIMZI_OAUTH_METRIC_REPORTERS, config.getValue(STRIMZI_OAUTH_METRIC_REPORTERS));
        this.configMap = configMap;

        this.metrics = initKafkaMetrics();
    }

    /**
     * Record a request time in millis.
     *
     * @param key SensorKey identifying the sensor
     * @param timeMs Time spent processing the request in millis
     */
    public void addTime(SensorKey key, long timeMs) {
        Sensor registeredSensor = sensorMap.computeIfAbsent(key, k -> {
            Sensor sensor = metrics.sensor(key.getId());
            addMetricsToSensor(metrics, sensor, key);
            return sensor;
        });

        registeredSensor.record(timeMs);
    }

    private Metrics initKafkaMetrics() {
        List<MetricsReporter> reporters = initReporters();
        KafkaMetricsContext ctx = createKafkaMetricsContext();

        MetricConfig metricConfig = getMetricConfig();

        return createMetrics(metricConfig, ctx, reporters);
    }

    private List<MetricsReporter> initReporters() {
        AbstractConfig kafkaConfig = initKafkaConfig();
        if (configMap.get(STRIMZI_OAUTH_METRIC_REPORTERS) != null) {
            return kafkaConfig.getConfiguredInstances(STRIMZI_OAUTH_METRIC_REPORTERS, MetricsReporter.class);
        }
        JmxReporter reporter = new JmxReporter();
        reporter.configure(configMap);
        return Collections.singletonList(reporter);
    }

    private AbstractConfig initKafkaConfig() {
        ConfigDef configDef = addMetricReporterToConfigDef(new ConfigDef(), STRIMZI_OAUTH_METRIC_REPORTERS);
        return new AbstractConfig(configDef, toMapOfStringValues(configMap));
    }

    private ConfigDef addMetricReporterToConfigDef(ConfigDef configDef, String name) {
        return configDef.define(name,
                ConfigDef.Type.LIST,
                Collections.emptyList(),
                new ConfigDef.NonNullValidator(),
                ConfigDef.Importance.LOW,
                CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC);
    }

    private Map<String, String> toMapOfStringValues(Map<String, ?> configMap) {
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, ?> ent: configMap.entrySet()) {
            Object val = ent.getValue();
            if (val == null) {
                continue;
            }
            if (val instanceof Class) {
                result.put(ent.getKey(), ((Class<?>) val).getCanonicalName());
            } else if (val instanceof List) {
                String stringVal = ((List<?>) val).stream().map(String::valueOf).collect(Collectors.joining(","));
                if (!stringVal.isEmpty()) {
                    result.put(ent.getKey(), stringVal);
                }
            } else {
                result.put(ent.getKey(), String.valueOf(ent.getValue()));
            }
        }
        return result;
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

    private void addMetricsToSensor(Metrics metrics, Sensor sensor, SensorKey key) {
        MetricName metricName = new MetricName("count", key.getName(), "Total request count", key.getAttributes());
        sensor.add(metricName, new CumulativeCount());

        metricName = new MetricName("totalTimeMs", key.getName(), "Total time spent in requests in ms", key.getAttributes());
        sensor.add(metricName, new CumulativeSum());

        metricName = new MetricName("avgTimeMs", key.getName(), "Average request time in ms", key.getAttributes());
        sensor.add(metricName, new Avg());

        metricName = new MetricName("maxTimeMs", key.getName(), "Max request time in ms", key.getAttributes());
        sensor.add(metricName, new Max());

        metricName = metrics.metricName("minTimeMs", key.getName(), "Min request time in ms", key.getAttributes());
        sensor.add(metricName, new Min());
    }

    /**
     * Get access to the underlying Kafka API Metrics object.
     *
     * Only used in KeycloakAuthorizer where it's part of the workaround for Kafka 4.1.0 breaking change.
     *
     * @return The underlying Kafka API Metrics instance used
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    // See https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei-may-expose-internal-representation-by-returning-reference-to-mutable-object-ei-expose-rep
    public Metrics getKafkaMetrics() {
        return metrics;
    }
}
