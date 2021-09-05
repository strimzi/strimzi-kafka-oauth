/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import java.util.Map;

/**
 * This class contains singleton components shared among Kafka Broker sessions
 */
public class Services {

    private static Services services;

    private final Sessions sessions = new Sessions();

    private final Validators validators = new Validators();

    private final Principals principals = new Principals();

    private final Credentials credentials = new Credentials();

    private Metrics metrics = new Metrics();

    public static synchronized void configure(Map<String, ?> configs) {
        if (services == null) {
            services = new Services();
        }
    }

    public static Services getInstance() {
        if (services == null) {
            throw new IllegalStateException("Services object has not been properly initialised");
        }
        return services;
    }

    public Validators getValidators() {
        return validators;
    }

    public static boolean isAvailable() {
        return services != null;
    }

    private Services() {
    }

    public Sessions getSessions() {
        return sessions;
    }

    public Principals getPrincipals() {
        return principals;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public Metrics getMetrics() {
        return metrics;
    }
}
