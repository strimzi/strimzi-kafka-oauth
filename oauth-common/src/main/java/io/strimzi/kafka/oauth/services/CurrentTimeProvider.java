/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

/**
 * The source of current time in millis as an alternative to calling <code>java.lang.System.currentTimeMillis()</code>
 */
public interface CurrentTimeProvider {

    /**
     * The default <code>CurrentTimeProvider</code> singleton which simply invokes <code>java.lang.System.currentTimeMillis()</code>
     */
    CurrentTimeProvider DEFAULT = System::currentTimeMillis;

    /**
     * Get the current time in millis
     *
     * @return Current time in millis
     */
    long currentTime();
}
