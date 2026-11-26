/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

public class TokenProviderTest {

    @Test
    public void testStaticTokenProvider() {
        final TokenProvider staticTokenProvider = new StaticTokenProvider("test-token");
        Assert.assertEquals("test-token", staticTokenProvider.token());
    }

    @Test
    public void testFileBasedTokenProvider() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath());

        final String tokenValueFromFile = fileBasedTokenProvider.token();
        final boolean delete = tempFile.delete();

        Assert.assertEquals("some-test-value", tokenValueFromFile);
        Assert.assertTrue(delete);
    }

    @Test
    public void testFileBasedTokenProviderWhenFileDoesNotExist() {
        try {
            new FileBasedTokenProvider("invalid-file-path");
            Assert.fail("failed to test for file type");

        } catch (IllegalArgumentException e) {
            Assert.assertTrue("No such file ...", e.getMessage().contains("No such file"));
        }
    }

    @Test
    public void testFileBasedTokenProviderWhenFileIsDir() {
        String tempDir = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        try {
            new FileBasedTokenProvider(tempDir);
            Assert.fail("failed to test for file existence");

        } catch (IllegalArgumentException e) {
            Assert.assertEquals("File is not a regular file: " + tempDir, e.getMessage());
        }
    }

    @Test
    public void testFileBasedTokenProviderWhenFileRemoved() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath());

        final boolean delete = tempFile.delete();
        Assert.assertTrue(delete);

        try {
            fileBasedTokenProvider.token();
            Assert.fail("this should not be possible");

        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testFileBasedTokenProviderWhenIgnoringPermissions() throws IOException {
        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));

        try {
            final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath(), true);
            Assert.assertEquals("some-test-value", fileBasedTokenProvider.token());
        } finally {
            Assert.assertTrue(tempFile.delete());
        }
    }

    @Test
    public void testFileBasedTokenProviderPermissionsWarningCanBeSuppressed() throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }

        final File tempFile = File.createTempFile("test-token-", ".jwt");
        Files.write(tempFile.toPath(), "some-test-value".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(tempFile.toPath(), PosixFilePermissions.fromString("rw-r--r--"));

        try {
            final String warned = captureStdErr(() -> new FileBasedTokenProvider(tempFile.getPath()));
            Assert.assertTrue("Owner-only permissions warning should be logged by default",
                    warned.contains("Permissions on token file should only give access to owner"));

            final String suppressed = captureStdErr(() -> {
                final TokenProvider fileBasedTokenProvider = new FileBasedTokenProvider(tempFile.getPath(), true);
                Assert.assertEquals("some-test-value", fileBasedTokenProvider.token());
            });
            Assert.assertFalse("Permissions warning should be suppressed",
                    suppressed.contains("Permissions on token file should only give access to owner"));
            Assert.assertFalse("Permissions check failure should also be suppressed",
                    suppressed.contains("Failed to check permissions on token file"));
        } finally {
            Assert.assertTrue(tempFile.delete());
        }
    }

    @Test
    public void testFileBasedTokenProviderIgnorePermissionsWhenFileDoesNotExist() {
        try {
            new FileBasedTokenProvider("invalid-file-path", true);
            Assert.fail("failed to test for file type");

        } catch (IllegalArgumentException e) {
            Assert.assertTrue("No such file ...", e.getMessage().contains("No such file"));
        }
    }

    @Test
    public void testFileBasedTokenProviderIgnorePermissionsWhenFileIsDir() {
        String tempDir = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
        try {
            new FileBasedTokenProvider(tempDir, true);
            Assert.fail("failed to test for file existence");

        } catch (IllegalArgumentException e) {
            Assert.assertEquals("File is not a regular file: " + tempDir, e.getMessage());
        }
    }

    private static String captureStdErr(Runnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream captureStream = new PrintStream(captured, true, StandardCharsets.UTF_8);
        try {
            System.setErr(captureStream);
            action.run();
            captureStream.flush();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(original);
            captureStream.close();
        }
    }
}
