/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A TokenProvider that uses a file as a token source.
 * The content of the file is fully read and returned every time a {@link io.strimzi.kafka.oauth.common.FileBasedTokenProvider#token()} method is called.
 * <p>
 * By default the file is checked to be readable only by its owner, and a warning is logged when it is not.
 * That check can be skipped when the file permissions cannot be restricted to the owner, for example on Kubernetes.
 */
public class FileBasedTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(io.strimzi.kafka.oauth.common.FileBasedTokenProvider.class);

    private final Path filePath;

    /**
     * Create a new instance that refers to a specified file as a token source.
     * <p>
     * The file is checked to be readable only by its owner. A warning is logged when it is not.
     *
     * @param tokenFilePath A path to a file containing a token
     * @throws IllegalArgumentException if the specified tokenFilePath does not exist or is not a regular file
     */
    public FileBasedTokenProvider(final String tokenFilePath) {
        this(tokenFilePath, false);
    }

    /**
     * Create a new instance that refers to a specified file as a token source.
     *
     * @param tokenFilePath A path to a file containing a token
     * @param ignorePermissions When {@code true}, skip the owner-only permissions check and do not log a warning
     *                          when the file is readable by other users. The file must still exist and be a regular file.
     *                          Use this when the token file is mounted with permissions that cannot be changed,
     *                          for example a Kubernetes secret volume.
     * @throws IllegalArgumentException if the specified tokenFilePath does not exist or is not a regular file
     */
    public FileBasedTokenProvider(final String tokenFilePath, final boolean ignorePermissions) {
        this.filePath = Paths.get(tokenFilePath);

        if (ignorePermissions) {
            IOUtil.ensureRegularFile(filePath);
            log.debug("Skipping token file permissions check [{}]", filePath);
            return;
        }

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
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "FileBasedTokenProvider: {path: '" + filePath + "'}";
    }
}
