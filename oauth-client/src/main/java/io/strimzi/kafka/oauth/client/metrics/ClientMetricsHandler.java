/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.kafka.oauth.client.metrics;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.MetricsHandler;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
import io.strimzi.kafka.oauth.services.Services;

import java.net.URI;
import java.util.Map;

public class ClientMetricsHandler implements MetricsHandler {
    private boolean enableMetrics;
    private OAuthMetrics metrics;
    private SensorKeyProducer authSensorKeyProducer;
    private SensorKeyProducer tokenSensorKeyProducer;


    /**
     * Records the time taken for a successful request and adds it to the metrics, if enabled.
     *
     * @param millis The time taken for the successful request in milliseconds.
     */
    @Override
    public void addSuccessRequestTime(long millis) {
        if (enableMetrics) {
            metrics.addTime(tokenSensorKeyProducer.successKey(), millis);
        }
    }

    /**
     * Records the time taken for a request resulting in an error and adds it to the metrics, if enabled.
     *
     * @param e      The Throwable representing the error.
     * @param millis The time taken for the request resulting in an error in milliseconds.
     */
    @Override
    public void addErrorRequestTime(Throwable e, long millis) {
        if (enableMetrics) {
            metrics.addTime(tokenSensorKeyProducer.errorKey(e), millis);
        }
    }

    /**
     * Configures metrics based on the provided configurations and initializes necessary components.
     *
     * @param configs        The configurations for the metrics.
     * @param config         The client configuration.
     * @param tokenEndpoint  The URI of the token endpoint.
     * @return The identifier of the client configuration.
     */
    public String configureMetrics(Map<String, ?> configs, ClientConfig config, URI tokenEndpoint) {
        String configId = config.getValue(Config.OAUTH_CONFIG_ID, "client");
        enableMetrics = config.getValueAsBoolean(Config.OAUTH_ENABLE_METRICS, false);

        authSensorKeyProducer = new ClientAuthenticationSensorKeyProducer(configId, tokenEndpoint);
        tokenSensorKeyProducer = tokenEndpoint != null ? new ClientHttpSensorKeyProducer(configId, tokenEndpoint) : null;

        if (!Services.isAvailable()) {
            Services.configure(configs);
        }
        if (enableMetrics) {
            metrics = Services.getInstance().getMetrics();
        }
        return configId;
    }

    /**
     * Records the time taken for a successful authentication request and adds it to the metrics, if enabled.
     *
     * @param startTimeMs The start time of the authentication request in milliseconds.
     */
    public void addSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    /**
     * Records the time taken for an authentication request resulting in an error and adds it to the metrics, if enabled.
     *
     * @param e           The Throwable representing the error.
     * @param startTimeMs The start time of the authentication request in milliseconds.
     */
    public void addErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }


    public boolean isEnableMetrics() {
        return enableMetrics;
    }
}
