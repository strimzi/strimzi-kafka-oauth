/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.testcontainers.containers.GenericContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class allows incremental reading of the log from a specified docker container.
 */
public class ContainerLogLineReader {

    private final GenericContainer<?> container;
    private int logLineOffset = 0;

    /**
     * Create a new instance of the log reader for the specified container.
     *
     * @param container The target container
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public ContainerLogLineReader(GenericContainer<?> container) {
        this.container = container;
    }

    /**
     * Fetch the whole log again and skip all the lines at the beginning that have already been read in the previous calls.
     *
     * @return Newly added lines in the log
     * @throws IOException If an operation fails
     */
    public List<String> readNext() throws IOException {
        List<String> lines = new ArrayList<>();
        String logs = container.getLogs();
        try (BufferedReader r = new BufferedReader(new StringReader(logs))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }

        List<String> result = lines.subList(logLineOffset, lines.size());
        logLineOffset = lines.size();
        return result;
    }
}
