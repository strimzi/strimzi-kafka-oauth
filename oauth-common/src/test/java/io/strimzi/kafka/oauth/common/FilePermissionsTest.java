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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import static io.strimzi.kafka.oauth.common.IOUtil.isFileAccessLimitedToOwner;
import static java.nio.file.Files.createSymbolicLink;

public class FilePermissionsTest {

    @Test
    public void filePermissionCheckTest() throws Exception {

        String filePath = "target/test1.txt";
        Path file = Paths.get(filePath);
        Assert.assertFalse("File should not yet exist", Files.exists(file));

        try {
            FileSystem fs = FileSystems.getDefault();
            Set<String> supportedViews = fs.supportedFileAttributeViews();
            if (supportedViews.contains("posix")) {
                // This part of the test only works in posix compatible environment
                // Create a file
                createFile(file, false);
                Assert.assertFalse("File should be accessible to group and others", isFileAccessLimitedToOwner(file));

                Files.delete(file);
            }

            createFile(file, true);
            Assert.assertTrue("File should NOT be accessible to group and others", isFileAccessLimitedToOwner(file));
        } finally {
            try {
                Files.delete(file);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void filePermissionWithSymlinksCheckTest() throws Exception {

        FileSystem fs = FileSystems.getDefault();
        Set<String> supportedViews = fs.supportedFileAttributeViews();
        if (supportedViews.contains("posix")) {

            String filePath = "target/test1.txt";
            Path file = Paths.get(filePath);
            Assert.assertFalse("File should not yet exist", Files.exists(file));

            // also check that symlinks don't exist
            Path link1File = Paths.get("target/link1");
            Path link2File = Paths.get("target/link2");
            Assert.assertFalse("Link1 should not yet exist", Files.exists(link1File, LinkOption.NOFOLLOW_LINKS));
            Assert.assertFalse("Link2 should not yet exist", Files.exists(link2File, LinkOption.NOFOLLOW_LINKS));

            try {
                createSymbolicLink(link1File, file.getFileName());
                createSymbolicLink(link2File, link1File.getFileName());

                // This part of the test only works in posix compatible environment
                // Create a file
                createFile(file, false);
                Assert.assertFalse("File should be accessible to group and others", isFileAccessLimitedToOwner(link2File));

                Files.delete(file);

                createFile(file, true);
                Assert.assertTrue("File should NOT be accessible to group and others", isFileAccessLimitedToOwner(file));

            } finally {
                for (Path f: Arrays.asList(file, link1File, link2File)) {
                    try {
                        Files.delete(f);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void createFile(Path file, boolean isPrivate) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        Set<String> supportedViews = fs.supportedFileAttributeViews();
        if (supportedViews.contains("posix")) {
            // Unix environment
            FileAttribute<Set<PosixFilePermission>> fileAttrs = PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString(isPrivate ? "rw-------" : "rw-r--r--"));

            Files.createFile(file, fileAttrs);

        } else if (supportedViews.contains("acl")) {
            // Windows environment
            Files.createFile(file);

            if (isPrivate) {
                AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
                UserPrincipal owner = view.getOwner();
                List<AclEntry> acl = view.getAcl();

                ListIterator<AclEntry> it = acl.listIterator();
                while (it.hasNext()) {
                    AclEntry entry = it.next();
                    if ("BUILTIN\\Administrators".equals(entry.principal().getName())
                            || "NT AUTHORITY\\SYSTEM".equals(entry.principal().getName())
                            || (owner.getName().equals(entry.principal().getName()) && AclEntryType.ALLOW == entry.type())) {
                        continue;
                    }
                    it.remove();
                }
                view.setAcl(acl);
            }
        } else {
            throw new RuntimeException("Not a POSIX or ACL compatible filesystem: " + fs);
        }
    }
}