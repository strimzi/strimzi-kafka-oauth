/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TestUtil {

    public static String unquote(String value) {
        return value.startsWith("\"") ?
                value.substring(1, value.length() - 1) :
                value;
    }

    /**
     * Get Kafka log by executing 'docker logs kafka', then extract only the entries
     * (possibly multi-line when there's a stacktrace) that contain the passed filter.
     *
     * @param filter The string to look for (not a regex) in the log
     * @return A list of lines from the log that match the filter (logging entries that contain the filter string)
     */
    public static List<String> getKafkaLogsForString(String filter) {
        try {
            boolean inmatch = false;
            ArrayList<String> result = new ArrayList<>();
            Pattern pat = Pattern.compile("\\[\\d\\d\\d\\d-\\d\\d-\\d\\d .*");

            Process p = Runtime.getRuntime().exec(new String[] {"docker", "logs", "kafka"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // is new logging entry?
                    if (pat.matcher(line).matches()) {
                        // contains the err string?
                        inmatch = line.contains(filter);
                    }
                    if (inmatch) {
                        result.add(line);
                    }
                }
            }

            return result;

        } catch (Throwable e) {
            throw new RuntimeException("Failed to get 'kafka' log", e);
        }
    }
}
