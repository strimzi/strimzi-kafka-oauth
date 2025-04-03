/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * A very inefficient but simple and good-enough-for-tests implementation of incrementally reading a log file and serving content as lines
 */
public class LogLineReader {

    static final int LOOP_PAUSE_MS = 1000;
    static final int TIMEOUT_SECONDS = 30;
    private final String logPath;
    private int logLineOffset = 0;

    public LogLineReader(String logPath) {
        this.logPath = logPath;
    }

    public List<String> waitFor(String condition) throws TimeoutException, InterruptedException {
        List<String> result = new ArrayList<>();
        TestUtil.waitForCondition(() -> {
            try {
                List<String> lines = readNext();
                int lineNum = TestUtil.findFirstMatchingInLog(lines, condition);
                if (lineNum >= 0) {
                    result.addAll(lines.subList(0, lineNum));
                    logLineOffset -= lines.size() - lineNum + 1;
                    return true;
                }
                result.addAll(lines);
                return false;
            } catch (Exception e) {
                throw new LogLineReaderException("Failed to read log", e);
            }
        }, LOOP_PAUSE_MS, TIMEOUT_SECONDS);

        return result;
    }

    public List<String> readNext() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(logPath));
        List<String> result = lines.subList(logLineOffset, lines.size());
        logLineOffset = lines.size();
        return result;
    }
}
