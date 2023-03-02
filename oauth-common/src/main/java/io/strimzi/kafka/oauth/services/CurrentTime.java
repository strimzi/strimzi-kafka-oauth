/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

/**
 * The source of time used in several places instead of <code>java.lang.System.currentTime</code>.
 *
 * It allows overriding the source of time for tests.
 */
public class CurrentTime {

    private static CurrentTimeProvider current = CurrentTimeProvider.DEFAULT;

    /**
     * Set the current time provider
     * @param timeProvider the <code>CurrentTimeProvider</code> instance
     */
    public static void setCurrentTimeProvider(CurrentTimeProvider timeProvider) {
        current = timeProvider;
    }

    /**
     * Get the currently set <code>CurrentTimeProvider</code>
     * @return The current <code>CurrentTimeProvider</code>
     */
    public static CurrentTimeProvider getCurrentTimeProvider() {
        return current;
    }

    /**
     * Get current time in millis
     *
     * @return Current time in millis provided by current <code>CurrentTimeProvider</code>
     */
    public static long currentTime() {
        return current.currentTime();
    }
}
