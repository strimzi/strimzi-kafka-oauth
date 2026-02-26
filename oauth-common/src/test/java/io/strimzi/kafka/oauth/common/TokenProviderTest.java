/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TokenProviderTest {

    @Test
    public void testStaticTokenProvider() {
        final TokenProvider staticTokenProvider = new StaticTokenProvider("test-token");
        Assertions.assertEquals(staticTokenProvider.token(), "test-token");
    }

    @Test
    public void testFileBasedTokenProvider() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath());

        final String tokenValueFromFile = fileBasedTokenProvider.token();
        final boolean delete = tempFile.delete();

        Assertions.assertEquals("some-test-value", tokenValueFromFile);
        Assertions.assertTrue(delete);
    }

    @Test
    public void testFileBasedTokenProviderWhenFileDoesNotExist() {
        try {
            new FileBasedTokenProvider("invalid-file-path");
            Assertions.fail("failed to test for file type");

        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("No such file"), "No such file ...");
        }
    }

    @Test
    public void testFileBasedTokenProviderWhenFileIsDir() {
        String tempDir = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        try {
            new FileBasedTokenProvider(tempDir);
            Assertions.fail("failed to test for file existence");

        } catch (IllegalArgumentException e) {
            Assertions.assertEquals("File is not a regular file: " + tempDir, e.getMessage());
        }
    }

    @Test
    public void testFileBasedTokenProviderWhenFileRemoved() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath());

        final boolean delete = tempFile.delete();
        Assertions.assertTrue(delete);

        try {
            fileBasedTokenProvider.token();
            Assertions.fail("this should not be possible");

        } catch (IllegalStateException e) {
            Assertions.assertTrue(e.getCause() instanceof IOException);
        }
    }
}
