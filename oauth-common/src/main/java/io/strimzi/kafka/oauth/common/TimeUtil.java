/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    public static String formatIsoDateTimeUTC(long timeMillis) {
        return LocalDateTime.ofEpochSecond(timeMillis / 1000, 0, ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
