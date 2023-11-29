/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class FilePermissionsTest {

    @Test
    public void filePermissionCheckTest() throws IOException {
        // if file does not exist there is nothing to check
        String filePath = "target/test1.txt";
        Path file = Paths.get(filePath);
        Assert.assertFalse("File should not yet exist", Files.exists(file));

        try {
            // Create a file
            createFile(file, false);
            Assert.assertFalse("File should be accessible to group and others", checkFileAccessLimitedToOwner(file));

            Files.delete(file);
            createFile(file, true);
            Assert.assertTrue("File should not be accessible to group and others", checkFileAccessLimitedToOwner(file));
        } finally {
            Files.delete(file);
        }
    }

    private void createFile(Path file, boolean isPrivate) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        Set<String> supportedViews = fs.supportedFileAttributeViews();
        if (supportedViews.contains("posix")) {
            FileAttribute<Set<PosixFilePermission>> fileAttrs = PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString(isPrivate ? "rw-------" : "rw-r--r--"));

            Files.createFile(file, fileAttrs);
        } else {
            throw new RuntimeException("Not a POSIX compatible filesystem: " + fs);
        }
    }


    /**
     * Check that there are zero permissions for group and others
     *
     * @param file Path object representing an existing file to check file permissions for
     * @return <code>true</code> if file permissions limit access to this file to the owner
     * @throws IllegalArgumentException if file doesn't exist or is not a regular file (not a directory)
     * @throws UnsupportedOperationException if filesystem doesn't support POSIX file permissions
     * @throws IOException if an I/O error occurs
     */
    public static boolean checkFileAccessLimitedToOwner(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("No such file: " + file.toAbsolutePath());
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File is not a regular file: " + file.toAbsolutePath());
        }

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
        List<PosixFilePermission> disallowed = Arrays.asList(
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

        // afterwards 'perms' will only contain disallowed permissions if any are present
        perms.retainAll(disallowed);
        return perms.isEmpty();
    }
}
