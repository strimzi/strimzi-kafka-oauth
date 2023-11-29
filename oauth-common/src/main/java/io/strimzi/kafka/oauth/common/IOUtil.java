/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * A helper class that contains commonly used methods for I/O operations
 */
public class IOUtil {

    private static final Random RANDOM = new Random();

    /**
     * Copy the content of specified InputStream into the specified OutputStream
     *
     * @param input The input stream to read from
     * @param output The output stream to write to
     * @throws IOException an exception if the copy operation fails
     */
    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[4096];

        int rc;
        try {
            while ((rc = input.read(buf)) != -1) {
                output.write(buf, 0, rc);
            }
        } finally {
            try {
                input.close();
            } catch (Exception ignored) { }
        }
    }

    /**
     * Generate a random 8 character string of hexadecimal digits
     *
     * @return a hexadecimal String of length 8
     */
    public static String randomHexString() {
        return randomHexString(8);
    }

    /**
     * Generate a random string of hexadecimal digits of the specified length
     *
     * @param length The length of the resulting string
     * @return a hexadecimal String of the specified length
     */
    public static String randomHexString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }

    /**
     * Convert a byte array into a hexadecimal string
     *
     * @param bytes A byte array
     * @return a hexadecimal string
     */
    public static String asHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            int val = 0xFF & b;
            if (val < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(val));
        }
        return sb.toString();
    }

    /**
     * Generate a predictable (deterministic) hash string of length 8 for an array of objects
     *
     * @param args The objects to take as input for the calculation
     * @return a hash string of length 8
     */
    public static String hashForObjects(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object o: args) {
            sb.append("|").append(o != null ? o : "\0\0\0");
        }
        String hex = asHexString(BigInteger.valueOf(crc32(sb.toString())).toByteArray());
        if (hex.length() == 8) {
            return hex;
        } else if (hex.length() > 8) {
            return hex.substring(hex.length() - 8);
        } else {
            return String.format("%8s", hex).replace(' ', '0');
        }
    }

    /**
     * Calculate a CRC32 for the specified String
     *
     * @param content The input string
     * @return CRC32 as long value
     */
    public static long crc32(String content) {
        CRC32 crc = new CRC32();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
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
    public static boolean isFileAccessLimitedToOwner(Path file) throws IOException {
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
