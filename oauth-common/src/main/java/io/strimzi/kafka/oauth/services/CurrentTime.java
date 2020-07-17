/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

public class CurrentTime {

    private static CurrentTimeProvider current = CurrentTimeProvider.DEFAULT;

    public static void setCurrentTimeProvider(CurrentTimeProvider timeProvider) {
        current = timeProvider;
    }

    public static CurrentTimeProvider getCurrentTimeProvider() {
        return current;
    }

    public static long currentTime() {
        return current.currentTime();
    }
}
