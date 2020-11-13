/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

public class LogUtil {

    /**
     * Return masked input text.
     *
     * Masking checks the length of input. If less than 8 it returns '**********'.
     * If less than 20 it prints out first letter in clear text, and then additional 9x '*' irrespective of actual input size.
     * If input length is greater than 20 chars, it prints out first 4 in clear text followed by '***..***' followed by last 4.
     *
     * The idea is to give some information for debugging while not leaking too much information about secrets.
     *
     * @param input     String with sensitive date which should be masked
     *
     * @return  The new masked string
     */
    public static String mask(String input) {
        if (input == null) {
            return null;
        }

        int len = input.length();
        if (len < 8) {
            return "**********";
        }

        if (len < 20) {
            return "" + input.charAt(0) + "*********";
        }

        return input.substring(0, 4) + "**" + input.substring(len - 4, len);
    }

    /**
     * Collect all exception causes into a single string
     *
     * @param e The exception
     * @return The message with all the causes
     */
    public static String getCauseMessage(Throwable e) {
        StringBuilder sb = new StringBuilder(e.toString());

        Throwable t = e;
        while ((t = t.getCause()) != null) {
            sb.append(", caused by: ").append(t.toString());
        }
        return sb.toString();
    }
}
