/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Map;

/**
 * This class contains singleton components shared among Kafka Broker sessions
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
public class Services {

    private static Services services;

    private final Map<String, ?> configs;

    private final Sessions sessions = new Sessions();

    private final Validators validators = new Validators();

    private final Principals principals = new Principals();

    private final Credentials credentials = new Credentials();

    private volatile OAuthMetrics metrics;

    /**
     * Configure a new <code>Services</code> instance. A new instance will only be created if one has not been configured before.
     *
     * @param configs Global configuration
     */
    public static synchronized void configure(Map<String, ?> configs) {
        if (services == null) {
            services = new Services(configs);
        }
    }

    /**
     * Get a configured singleton instance
     *
     * @return Services object
     */
    public static Services getInstance() {
        if (services == null) {
            throw new IllegalStateException("Services object has not been properly initialised");
        }
        return services;
    }

    private Services(Map<String, ?> configs) {
        this.configs = configs;
    }

    /**
     * Get {@link Validators} singleton
     *
     * @return Validators instance
     */
    public Validators getValidators() {
        return validators;
    }

    /**
     * Check if Services singleton has been configured
     *
     * @return True if configured
     */
    public static boolean isAvailable() {
        return services != null;
    }

    /**
     * Get {@link Sessions} singleton
     *
     * @return Sessions instance
     */
    public Sessions getSessions() {
        return sessions;
    }

    /**
     * Get {@link Principals} singleton
     *
     * @return Principals instance
     */
    public Principals getPrincipals() {
        return principals;
    }

    /**
     * Get {@link Credentials} singleton
     *
     * @return Credentials instance
     */
    public Credentials getCredentials() {
        return credentials;
    }

    /**
     * Get {@link OAuthMetrics} singleton
     *
     * @return OAuthMetrics instance
     */
    public OAuthMetrics getMetrics() {
        if (metrics == null) {
            synchronized (Services.class) {
                if (metrics == null) {
                    metrics = new OAuthMetrics(configs);
                }
            }
        }
        return metrics;
    }
}
