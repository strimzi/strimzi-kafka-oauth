/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.CurrentTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockCurrentTimeProvider implements CurrentTimeProvider {

    private static final Logger log = LoggerFactory.getLogger(MockCurrentTimeProvider.class);

    private long diff = 0;

    public void setTime(long millis) {
        diff = millis - System.currentTimeMillis();
        log.debug("New current time is: " + (System.currentTimeMillis() + diff));
    }

    public long addSeconds(int seconds) {
        diff += 1000 * seconds;
        log.debug("New current time is: plus " + seconds + "s: " + (System.currentTimeMillis() + diff));
        return currentTime();
    }

    @Override
    public long currentTime() {
        return System.currentTimeMillis() + diff;
    }
}
