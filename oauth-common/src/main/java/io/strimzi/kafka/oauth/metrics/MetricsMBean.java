/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsMBean implements DynamicMBean {

    private final Object lock = new Object();

    private final String name;
    private final String description;
    private final ConcurrentHashMap<String, CounterMetric> attrs;

    public MetricsMBean(String name, String description, Map<String, CounterMetric> metrics) {
        this.name = name;
        this.description = description;
        this.attrs = new ConcurrentHashMap<>(metrics);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException {
        CounterMetric attr = attrs.get(attribute);
        if (attr == null) {
            throw new AttributeNotFoundException("No such attribute: " + attribute + " on mbean: " + name);
        }
        return attr.getValue();
    }

    @Override
    public void setAttribute(Attribute attribute) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        synchronized (lock) {
            AttributeList ls = new AttributeList();
            for (Map.Entry<String, CounterMetric> ent : attrs.entrySet()) {
                ls.add(new Attribute(ent.getKey(), ent.getValue().getValue()));
            }

            return ls;
        }
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> info = new LinkedList<>();
        for (Map.Entry<String, CounterMetric> ent : attrs.entrySet()) {
            info.add(new MBeanAttributeInfo(ent.getKey(), ent.getValue().getValueType(), ent.getValue().getDescription(), true, false, false));
        }

        return new MBeanInfo(getClass().getName(), description, info.toArray(new MBeanAttributeInfo[info.size()]), null, null, null);
    }

    public Object getLock() {
        return lock;
    }
}
