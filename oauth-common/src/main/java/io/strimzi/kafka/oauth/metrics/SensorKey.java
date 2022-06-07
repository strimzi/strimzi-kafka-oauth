/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The SensorKey represents and uniquely identifies the Kafka Metrics API Sensor,
 * which represents a set of metrics that are updated at the same time.
 * <br><br>
 * These metrics are:
 *
 * <ul>
 *     <li>count</li>
 *     <li>totalTimeMs</li>
 *     <li>minTimeMs</li>
 *     <li>maxTimeMs</li>
 *     <li>avgTimeMs</li>
 * </ul>
 *
 * The SensorKey is composed of the {@code name} and the {@code attributes}.
 * <br><br>
 * The following attributes are required: "context", "kind", "host", "path".
 * <br><br>
 * As a convention, only use alphanumeric characters and an underscore (_) in the name and the attribute keys.
 * Note, that names and attributes become part of the JMX ObjectName when metrics are plugged into the JMX MBeanServer.
 * Attribute values containing some other characters (like ':', or '/') will become quoted when converted to JMX ObjectNames.
 * <br><br>
 * Kafka Sensors are created on demand and cached by the value of the {@code id}, which is a string representation of this SensorKey.
 * Each Sensor is exported as a single managed bean with a unique JMX ObjectName.
 * <br><br>
 * Some examples of SensorKeys and how they map to JMX ObjectNames:
 *
 *     <ul><li> Name: <em>http_requests </em></li>
 *         <li> Attributes: <em>context=PUBLIC, kind=jwks, host=localhost:443, path=/certs, outcome=success, status=200 </em></li>
 *         <li> JMX ObjectName: <em>strimzi.oauth:type=http_requests,context=PUBLIC,kind=jwks,host="localhost:443",path="/certs",outcome=success,status=200</em></li>
 *     </ul>
 *
 *     <ul><li> Name: <em>validation_requests </em></li>
 *         <li> Attributes: <em>context=e8af84dd, kind=introspect, host=localhost:443, path=/introspect, mechanism=PLAIN, outcome=error, error_type=other</em></li>
 *         <li> JMX ObjectName: <em>strimzi.oauth:type=validation_requests,context=e8af84dd,kind=introspect,host="localhost:443",path="/introspect",mechanism=PLAIN,outcome=error,error_type=other</em></li>
 *     </ul>
 */
public class SensorKey {

    private static final String[] SORTED_ATTRIBUTES = {"context", "kind", "host", "path"};

    final String name;
    final Map<String, String> attributes;
    final String id;

    /**
     * Create a new instance of SensorKey from name and a map of attributes
     *
     * @param name The metric name
     * @param attributes The map of attributes
     * @return The new instance of SensorKey
     */
    public static SensorKey of(String name, Map<String, String> attributes) {
        return new SensorKey(name, attributes);
    }

    /**
     * Create a new instance of SensorKey from name and attributes
     *
     * @param name The metric name
     * @param attributes The varargs list of attributes as name1, value1 ... nameN, valueN
     * @return The new instance of SensorKey
     */
    public static SensorKey of(String name, String... attributes) {
        if (attributes.length == 0) {
            throw new IllegalArgumentException("There should be some attributes");
        }
        if (attributes.length % 2 != 0) {
            throw new IllegalArgumentException("There should be an even number of attributes");
        }

        Map<String, String> attrMap = new LinkedHashMap<>();
        for (int i = 0; i < attributes.length; i += 2) {
            attrMap.put(attributes[i], attributes[i + 1]);
        }
        return new SensorKey(name, attrMap);
    }

    private SensorKey(String name, Map<String, String> attributes) {
        this.name = name;
        this.attributes = Collections.unmodifiableMap(sortAttributes(attributes));
        this.id = name + attributes;
    }

    private LinkedHashMap<String, String> sortAttributes(Map<String, String> attributes) {
        TreeMap<String, String> ordered = new TreeMap<>(attributes);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String key: SORTED_ATTRIBUTES) {
            result.put(key, requireKey(ordered, key));
        }
        result.putAll(ordered);
        return result;
    }

    private String requireKey(TreeMap<String, String> ordered, String key) {
        String value = ordered.remove(key);
        if (value == null) {
            throw new IllegalArgumentException("The required attribute is missing: " + key);
        }
        return value;
    }

    /**
     * Get a String representation of this SensorKey that uniquely identifies it.
     *
     * This is simply a concatenation of the name and the string value of the attributes map.
     *
     * @return A sensor id
     */
    public String getId() {
        return id;
    }

    /**
     * Get the name of this SensorKey
     *
     * @return The name as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Get the attributes of this SensorKey as a Map
     *
     * @return The attributes as a Map
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorKey sensorKey = (SensorKey) o;
        return Objects.equals(name, sensorKey.name) && Objects.equals(attributes, sensorKey.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attributes);
    }

    @Override
    public String toString() {
        return getClass().getName() + "@" + hashCode() + " " + id;
    }
}
