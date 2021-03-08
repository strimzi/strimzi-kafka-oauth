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

    private Sessions sessions = new Sessions();

    private Validators validators = new Validators();

    private Principals principals = new Principals();

    private Credentials credentials = new Credentials();

    public static void configure(Map<String, ?> configs) {
        services = new Services();
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
}
