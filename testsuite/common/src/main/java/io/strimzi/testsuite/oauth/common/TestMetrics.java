/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.common;

import io.strimzi.kafka.oauth.common.HttpUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.strimzi.testsuite.oauth.common.TestUtil.unquote;

public class TestMetrics {

    ArrayList<MetricEntry> entries = new ArrayList<>();


    /**
     * Get response from prometheus endpoint as a map of key:value pairs
     * We expect the response to be a 'well formed' key=value document in the sense that each line contains a '=' sign
     *
     * @param metricsEndpointUri The endpoint used to fetch metrics
     * @return Metrics object
     * @throws IOException If http request to retrieve Prometheus metrics failed
     */
    public static TestMetrics getPrometheusMetrics(URI metricsEndpointUri) throws IOException {
        String response = HttpUtil.get(metricsEndpointUri, null, null, null, String.class);

        TestMetrics metrics = new TestMetrics();
        //Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new StringReader(response))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                String key = null;
                Map<String, String> attrs = new LinkedHashMap<>();

                int endPos;
                int pos = line.indexOf('{');

                if (pos != -1) {
                    key = line.substring(0, pos);

                    endPos = line.lastIndexOf("}");
                    String attrsPart = line.substring(pos + 1, endPos);
                    String[] attrsArray = attrsPart.split(",");

                    for (String attr : attrsArray) {
                        String[] keyVal = attr.split("=");
                        if (keyVal.length != 2) {
                            // skip mis-parsed attribute values due to ',' inside a quoted value
                            // the entries we are interested in should never have comma in the attribute
                            continue;
                        }
                        attrs.put(keyVal[0], keyVal[1].substring(1, keyVal[1].length() - 1));
                    }
                }
                endPos = line.lastIndexOf(" ");
                if (key == null) {
                    key = line.substring(0, endPos);
                }
                String value = line.substring(endPos + 1);
                metrics.addMetric(key, attrs, unquote(value));
            }
        }
        return metrics;
    }

    void addMetric(String key, Map<String, String> attrs, String value) {
        entries.add(new MetricEntry(key, attrs, value));
    }

    /**
     * Returns a value of a single metric matching the key and the attributes.
     *
     * Attributes are specified as: name1, value1, name2, value2, ...
     * Not all attributes have to be specified, but those specified have to match (equality).
     *
     * @param key Metric name to retrieve
     * @param attrs The attributes filter passed as attrName1, attrValue1, attrName2, attrValue2 ...
     * @return Metric value as String
     */
    public String getValue(String key, String... attrs) {
        boolean match = false;
        String result = null;
        next:
        for (MetricEntry entry: entries) {
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
     * Get the sum of values of all the metrics matching the key and the attributes
     *
     * Attributes are specified as: name1, value1, name2, value2, ...
     * Not all attributes have to be specified, but those specified have to match (equality).
     *
     * @param key Metric key
     * @param attrs The attributes filter passed as attrName1, attrValue1, attrName2, attrValue2 ...
     * @return The sum of the values of all the matching metrics as String
     */
    public BigDecimal getValueSum(String key, String... attrs) {

        BigDecimal result = new BigDecimal(0);
        next:
        for (MetricEntry entry: entries) {
            if (entry.key.equals(key)) {
                for (int i = 0; i < attrs.length; i += 2) {
                    if (!attrs[i + 1].equals(entry.attrs.get(attrs[i]))) {
                        continue next;
                    }
                }
                result = result.add(new BigDecimal(entry.value));
            }
        }
        return result;
    }

    static class MetricEntry {
        String key;
        Map<String, String> attrs;
        String value;

        MetricEntry(String key, Map<String, String> attrs, String value) {
            this.key = key;
            this.attrs = attrs;
            this.value = value;
        }
    }
}

