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

public interface TokenProvider {

    String token();

    class StaticTokenProvider implements TokenProvider {
        private final String token;

        public StaticTokenProvider(final String token) {
            this.token = token;
        }

        @Override
        public String token() {
            return token;
        }
    }

    class FileBasedTokenProvider implements TokenProvider {
        private final Path filePath;

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
