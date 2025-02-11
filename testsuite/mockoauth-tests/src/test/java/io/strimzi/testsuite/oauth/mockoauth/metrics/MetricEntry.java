/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth.metrics;

import java.util.Map;

public class MetricEntry {
    String key;
    Map<String, String> attrs;
    String value;

    MetricEntry(String key, Map<String, String> attrs, String value) {
        this.key = key;
        this.attrs = attrs;
        this.value = value;
    }

    @Override
    public String toString() {
        return "MetricEntry{" +
                "key='" + key + '\'' +
                ", attrs=" + attrs +
                ", value='" + value + '\'' +
                '}';
    }
}
