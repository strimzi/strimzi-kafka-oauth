/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TokenProviderTest {

    @Test
    public void testStaticTokenProvider() {

        final TokenProvider staticTokenProvider = new TokenProvider.StaticTokenProvider("test-token");

        Assert.assertEquals(staticTokenProvider.token(), "test-token");
    }

    @Test
    public void testFileBasedTokenProvider() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new TokenProvider.FileBasedTokenProvider(tempFile.getPath());

        final String tokenValueFromFile = fileBasedTokenProvider.token();
        final boolean delete = tempFile.delete();

        Assert.assertEquals("some-test-value", tokenValueFromFile);
        Assert.assertTrue(delete);
    }

    @Test
    public void testFileBasedTokenProvider_fileDoesNotExist() {
        try {
            final TokenProvider fileBasedTokenProvider = new TokenProvider.FileBasedTokenProvider("/invalid-file-path");
            Assert.fail("failed to test for file type");

        } catch (IllegalArgumentException e) {
            Assert.assertEquals("file '/invalid-file-path' does not exist!", e.getMessage());
        }
    }

    @Test
    public void testFileBasedTokenProvider_fileIsDir() {
        try {
            final TokenProvider fileBasedTokenProvider = new TokenProvider.FileBasedTokenProvider(System.getProperty("java.io.tmpdir"));
            Assert.fail("failed to test for file existence");

        } catch (IllegalArgumentException e) {
            Assert.assertEquals("'/tmp' does not point to a file!", e.getMessage());
        }
    }

    @Test
    public void testFileBasedTokenProvider_fileRemoved() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new TokenProvider.FileBasedTokenProvider(tempFile.getPath());

        final boolean delete = tempFile.delete();
        Assert.assertTrue(delete);

        try {
            fileBasedTokenProvider.token();
            Assert.fail("this should not be possible");

        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getCause() instanceof IOException);
        }
    }
}
