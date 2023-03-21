/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth.metrics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

public class Metrics {
    ArrayList<MetricEntry> entries = new ArrayList<>();

    public void addMetric(String key, Map<String, String> attrs, String value) {
        entries.add(new MetricEntry(key, attrs, value));
    }

    /**
     * Returns a value of a single metric matching the key and the attributes.
     * Not all attributes have to be specified.
     *
     * @param key The key identifying the metric
     * @param attrs The attributes filter passed as [attrName1, attrValue1, ... attrNameN, attrValueN]
     * @return Metric value as String
     */
    String getValue(String key, String... attrs) {
        boolean match = false;
        String result = null;
        next:
        for (MetricEntry entry : entries) {
            if (entry.key.equals(key)) {
                for (int i = 0; i < attrs.length; i += 2) {
                    if (!attrs[i + 1].equals(entry.attrs.get(attrs[i]))) {
                        continue next;
                    }
                }
                if (!match) {
                    match = true;
                    result = entry.value;
                } else {
                    throw new RuntimeException("More than one matching metric entry");
                }
            }
        }
        return result;
    }

    /**
     * Get the sum of values of all the matching metrics
     *
     * @param key The key identifying the metric
     * @param attrs The attributes filter passed as [attrName1, attrValue1, ... attrNameN, attrValueN]
     * @return The sum of the values of the matching metrics as string
     */
    String getValueSum(String key, String... attrs) {

        BigDecimal result = new BigDecimal(0);
        next:
        for (MetricEntry entry : entries) {
            if (entry.key.equals(key)) {
                for (int i = 0; i < attrs.length; i += 2) {
                    if (!attrs[i + 1].equals(entry.attrs.get(attrs[i]))) {
                        continue next;
                    }
                }
                result = result.add(new BigDecimal(entry.value));
            }
        }
        return result.toPlainString();
    }

    static String quoted(String value) {
        return "\\\"" + value + "\\\"";
    }
}
