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
