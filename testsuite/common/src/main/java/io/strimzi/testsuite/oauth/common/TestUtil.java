/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
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
     * @param filters Strings to look for (not a regex) in the log - they all must be present in a line for the line to match
     * @return A list of lines from the log that match the filter (logging entries that contain the filter string)
     */
    @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
    public static List<String> getContainerLogsForString(String containerName, String... filters) {
        try {
            boolean inmatch = false;
            ArrayList<String> result = new ArrayList<>();
            Pattern pat = Pattern.compile("\\[\\d\\d\\d\\d-\\d\\d-\\d\\d .*");

            Process p = Runtime.getRuntime().exec(new String[] {"docker", "logs", containerName});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // is new logging entry?
                    if (pat.matcher(line).matches()) {
                        // contains the err string?
                        // all filters have to match
                        for (String filter: filters) {
                            inmatch = line.contains(filter);
                            if (!inmatch) {
                                break;
                            }
                        }
                    }
                    if (inmatch) {
                        result.add(line);
                    }
                }
            }
            return result;

        } catch (Throwable e) {
            throw new RuntimeException("Failed to get '" + containerName + "' log", e);
        }
    }

    /**
     * Helper method to wait for a condition by periodically testing the condition until it is satisfied or until timeout.
     *
     * @param condition The condition to test
     * @param loopPauseMs A pause between two repeats in millis
     * @param timeoutSeconds A timeout in seconds
     * @throws TimeoutException An exception thrown if condition not satisfied within a timeout
     * @throws InterruptedException An exception thrown if interrupted
     */
    public static void waitForCondition(Supplier<Boolean> condition, int loopPauseMs, int timeoutSeconds) throws TimeoutException, InterruptedException {
        long startTime = System.currentTimeMillis();
        boolean done;
        do {
            done = condition.get();
            if (!done) {
                // Condition not met
                if (System.currentTimeMillis() + loopPauseMs - startTime >= timeoutSeconds * 1000L) {
                    throw new TimeoutException("Condition not met in " + timeoutSeconds + " seconds");
                }
                Thread.sleep(loopPauseMs);
            }
        } while (!done);
    }

    public static void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();
    }

    public static int findFirstMatchingInLog(List<String> log, String regex) {
        int lineNum = 0;
        Pattern pattern = Pattern.compile(regex);
        for (String line: log) {
            if (pattern.matcher(line).find()) {
                return lineNum;
            }
            lineNum++;
        }
        return -1;
    }

    public static boolean checkLogForRegex(List<String> log, String regex) {
        return findFirstMatchingInLog(log, regex) != -1;
    }

    public static int countLogForRegex(List<String> log, String regex) {
        int count = 0;
        Pattern pattern = Pattern.compile(regex);
        for (String line: log) {
            if (pattern.matcher(line).find()) {
                count += 1;
            }
        }
        return count;
    }
}
