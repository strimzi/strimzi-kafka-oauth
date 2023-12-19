/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A TokenProvider that uses a file as a token source.
 * The content of the file is fully read and returned every time a {@link io.strimzi.kafka.oauth.common.FileBasedTokenProvider#token()} method is called.
 */
public class FileBasedTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(io.strimzi.kafka.oauth.common.FileBasedTokenProvider.class);

    private final Path filePath;

    /**
     * Create a new instance that refers to a specified file as a token source.
     *
     * @param tokenFilePath A path to a file containing a token
     * @throws IllegalArgumentException if the specified tokenFilePath does not exist or is not a regular file
     */
    public FileBasedTokenProvider(final String tokenFilePath) {
        this.filePath = Paths.get(tokenFilePath);

        try {
            if (!IOUtil.isFileAccessLimitedToOwner(filePath)) {
                log.warn("Permissions on token file should only give access to owner [{}]", filePath);
            }
        } catch (IllegalArgumentException e) {
            // received when file does not exist. re-throw an exception
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check permissions on token file [{}]:", filePath, e);
        }
    }

    @Override
    public String token() {
        try {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "FileBasedTokenProvider: {path: '" + filePath + "'}";
    }
}
