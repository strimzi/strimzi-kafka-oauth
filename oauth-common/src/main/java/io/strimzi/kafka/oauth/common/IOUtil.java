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
import java.util.Random;
import java.util.zip.CRC32;

public class IOUtil {

    private static final Random RANDOM = new Random();

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

    public static String randomHexString() {
        return randomHexString(8);
    }

    public static String randomHexString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }

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

    public static long crc32(String content) {
        CRC32 crc = new CRC32();
        crc.update(content.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
