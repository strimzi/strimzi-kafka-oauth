/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MetricKey {

    private static final Set<String> QUOTED_ATTRS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("host", "path")));
    private static final List<String> SORTED_ATTRS = Arrays.asList("context", "mechanism", "type", "host", "path", "outcome", "error_type", "status");

    final String name;
    final Map<String, String> attrs;

    public MetricKey(String name, Map<String, String> attrs) {
        this.name = composeMBeanName(name, attrs);
        this.attrs = Collections.unmodifiableMap(attrs);
    }


    public static MetricKey of(String name, Map<String, String> attrs) {
        return new MetricKey(name, attrs);
    }

    public static MetricKey of(String name, String... attrs) {
        if (attrs.length == 0) {
            throw new IllegalArgumentException("There should be some attrs");
        }
        if (attrs.length % 2 != 0) {
            throw new IllegalArgumentException("There should be an even number of attrs");
        }

        Map<String, String> attrMap = new HashMap<>();
        for (int i = 0; i < attrs.length; i += 2) {
            attrMap.put(attrs[i], attrs[i + 1]);
        }
        return new MetricKey(name, attrMap);
    }

    public String getMBeanName() {
        return name;
    }

    private String composeMBeanName(String name, Map<String, String> attrs) {
        StringBuilder objName = new StringBuilder("strimzi.oauth:").append("name=").append(name);
        Map<String, String> allAttrs = new HashMap<>(attrs);

        for (String attr: SORTED_ATTRS) {
            String val = allAttrs.remove(attr);
            if (val != null) {
                appendAttrToName(objName, attr, val);
            }
        }
        for (Map.Entry<String, String> ent: allAttrs.entrySet()) {
            appendAttrToName(objName, ent.getKey(), ent.getValue());
        }

        return objName.toString();
    }

    private void appendAttrToName(StringBuilder objName, String attr, String val) {
        objName.append(",").append(attr).append("=");
        if (QUOTED_ATTRS.contains(attr)) {
            objName.append("\"").append(val).append("\"");
        } else {
            objName.append(val);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricKey metricKey = (MetricKey) o;
        return Objects.equals(name, metricKey.name) && Objects.equals(attrs, metricKey.attrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attrs);
    }
}
