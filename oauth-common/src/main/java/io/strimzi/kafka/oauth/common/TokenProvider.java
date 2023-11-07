/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A contract for a class that provides a token
 */
public interface TokenProvider {

    /**
     * Get a token
     *
     * @return A token
     */
    String token();

    /**
     * A TokenProvider that contains an immutable token that is returned every time a {@link StaticTokenProvider#token()} method is called.
     */
    class StaticTokenProvider implements TokenProvider {
        private final String token;

        /**
         * Create a new instance with a token that never changes
         *
         * @param token A token
         */
        public StaticTokenProvider(final String token) {
            this.token = token;
        }

        @Override
        public String token() {
            return token;
        }
    }

    /**
     * A TokenProvider that uses a file as a token source.
     * The content of the file is fully read and returned every time a {@link FileBasedTokenProvider#token()} method is called.
     */
    class FileBasedTokenProvider implements TokenProvider {
        private final Path filePath;

        /**
         * Create a new instance that refers to a specified file as a token source.
         *
         * @param tokenFilePath A path to a file containing a token
         */
        public FileBasedTokenProvider(final String tokenFilePath) {
            this.filePath = Paths.get(tokenFilePath);
            if (!filePath.toFile().exists()) {
                throw new IllegalArgumentException("file '" + filePath + "' does not exist!");
            }
            if (!filePath.toFile().isFile()) {
                throw new IllegalArgumentException("'" + filePath + "' does not point to a file!");
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
    }
}
