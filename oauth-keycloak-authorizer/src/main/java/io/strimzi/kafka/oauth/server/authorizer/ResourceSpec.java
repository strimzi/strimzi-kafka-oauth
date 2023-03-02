/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.Locale;

/**
 * ResourceSpec is used to parse a resource matching pattern and to perform matching to a specific resource.
 */
public class ResourceSpec {

    /**
     * Kafka resource types
     */
    public enum ResourceType {
        /**
         * TOPIC
         */
        TOPIC,

        /**
         * GROUP
         */
        GROUP,

        /**
         * CLUSTER
         */
        CLUSTER,

        /**
         * TRANSACTIONAL_ID
         */
        TRANSACTIONAL_ID,

        /**
         * DELEGATION_TOKEN
         */
        DELEGATION_TOKEN
    }

    private String clusterName;
    private boolean clusterStartsWith;

    private ResourceType resourceType;
    private String resourceName;
    private boolean resourceStartsWith;


    /**
     * Get a 'kafka-cluster' value (not the same as Kafka <code>Cluster</code> resource type).
     * For example: <code>kafka-cluster:my-cluster,Cluster:kafka-cluster</code>
     *
     * @return Cluster name
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * See if 'kafka-cluster' specification uses an asterisk as in <code>kafka-cluster:*,Topic:orders</code>
     *
     * @return True if value ends with an asterisk
     */
    public boolean isClusterStartsWith() {
        return clusterStartsWith;
    }

    /**
     * Get a resource type
     *
     * @return ResourceType enum value
     */
    public ResourceType getResourceType() {
        return resourceType;
    }

    /**
     * Get resource name
     *
     * @return A resource name
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * See if a resource specification uses an asterisk as in <code>Topic:orders_*</code>
     *
     * @return True if value ends with an asterisk
     */
    public boolean isResourceStartsWith() {
        return resourceStartsWith;
    }

    /**
     * Match specific resource's cluster, type and name to this ResourceSpec
     *
     * If clusterName is set then cluster must match, otherwise cluster match is ignored.
     * Type and name are always matched.
     *
     * @param cluster Kafka cluster name such as: my-kafka
     * @param type Resource type such as: Topic, Group
     * @param name Resource name such as: my-topic
     * @return true if cluster, type and name match this resource spec
     */
    public boolean match(String cluster, String type, String name) {
        if (clusterName != null) {
            if (cluster == null) {
                throw new IllegalArgumentException("cluster == null");
            }
            if (clusterStartsWith) {
                if (!cluster.startsWith(clusterName)) {
                    return false;
                }
            } else if (!cluster.equals(clusterName)) {
                return false;
            }
        }

        if (type == null) {
            throw new IllegalArgumentException("type == null");
        }
        if (resourceType == null || !type.equals(resourceType.name())) {
            return false;
        }

        if (name == null) {
            throw new IllegalArgumentException("name == null");
        }
        if (resourceStartsWith) {
            return name.startsWith(resourceName);
        } else {
            return name.equals(resourceName);
        }
    }

    /**
     * A factory method to parse a ResourceSpec from a string
     *
     * @param name Resource spec as a string
     * @return A ResourceSpec instance
     */
    public static ResourceSpec of(String name) {
        ResourceSpec spec = new ResourceSpec();

        String[] parts = name.split(",");
        for (String part: parts) {
            String[] subSpec = part.split(":");
            if (subSpec.length != 2) {
                throw new IllegalArgumentException("Failed to parse Resource: " + name + " - part doesn't follow TYPE:NAME pattern: " + part);
            }

            String type = subSpec[0].toLowerCase(Locale.US);
            String pat = subSpec[1];
            if (type.equals("kafka-cluster")) {
                if (spec.clusterName != null) {
                    throw new IllegalArgumentException("Failed to parse Resource: " + name + " - cluster part specified multiple times");
                }
                if (pat.endsWith("*")) {
                    spec.clusterName = pat.substring(0, pat.length() - 1);
                    spec.clusterStartsWith = true;
                } else {
                    spec.clusterName = pat;
                }
                continue;
            }

            if (spec.resourceName != null) {
                throw new IllegalArgumentException("Failed to parse Resource: " + name + " - resource part specified multiple times");
            }

            switch (type) {
                case "topic":
                    spec.resourceType = ResourceType.TOPIC;
                    break;
                case "group":
                    spec.resourceType = ResourceType.GROUP;
                    break;
                case "cluster":
                    spec.resourceType = ResourceType.CLUSTER;
                    break;
                case "transactionalid":
                    spec.resourceType = ResourceType.TRANSACTIONAL_ID;
                    break;
                case "delegationtoken":
                    spec.resourceType = ResourceType.DELEGATION_TOKEN;
                    break;
                default:
                    throw new IllegalArgumentException("Failed to parse Resource: " + name + " - unsupported segment type: " + subSpec[0]);
            }

            if (pat.endsWith("*")) {
                spec.resourceName = pat.substring(0, pat.length() - 1);
                spec.resourceStartsWith = true;
            } else {
                spec.resourceName = pat;
            }
        }

        return spec;
    }

    @Override
    public String toString() {
        return (clusterName != null ?
                    ("kafka-cluster:" + clusterName + (clusterStartsWith ? "*" : "") + ",")
                        : "") +
                (resourceName != null ?
                    (resourceType + ":" + resourceName + (resourceStartsWith ? "*" : ":"))
                        : "");
    }
}
