/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

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
            } catch (Exception e) { }
        }
    }

    public static String randomHash() {
        return randomHash(8);
    }

    public static String randomHash(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
