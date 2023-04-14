/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * A very inefficient but simple and good-enough-for-tests implementation of incrementally reading a log file and serving content as lines
 */
public class LogLineReader {

    private final String logPath;
    private int logLineOffset = 0;

    LogLineReader(String logPath) {
        this.logPath = logPath;
    }

    List<String> readNext() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(logPath));
        List<String> result = lines.subList(logLineOffset, lines.size());
        logLineOffset = lines.size();
        return result;
    }
}
