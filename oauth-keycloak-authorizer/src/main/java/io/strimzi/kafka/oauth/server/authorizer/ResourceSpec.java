/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.Locale;

/**
 * ResourceSpec is used to parse resource matching pattern and to perform matching to apecific resource.
 *
 */
public class ResourceSpec {

    public enum ResourceType {
        Topic,
        Group,
        Cluster
    }

    private String clusterName;
    private boolean clusterStartsWith;

    private ResourceType resourceType;
    private String resourceName;
    private boolean resourceStartsWith;


    public String getClusterName() {
        return clusterName;
    }

    public boolean isClusterStartsWith() {
        return clusterStartsWith;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

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
        if (!type.equals(resourceType.name())) {
            return false;
        }

        if (name == null) {
            throw new IllegalArgumentException("name == null");
        }
        if (resourceStartsWith) {
            if (!name.startsWith(resourceName)) {
                return false;
            }
        } else if (!name.equals(resourceName)) {
            return false;
        }

        return true;
    }

    public static ResourceSpec of(String name) {
        ResourceSpec spec = new ResourceSpec();

        String[] parts = name.split(",");
        for (String part: parts) {
            String[] subSpec = part.split(":");
            if (subSpec.length != 2) {
                throw new RuntimeException("Failed to parse Resource: " + name + " - part doesn't follow TYPE:NAME pattern: " + part);
            }

            String type = subSpec[0].toLowerCase(Locale.US);
            String pat = subSpec[1];
            if (type.equals("kafka-cluster")) {
                if (spec.clusterName != null) {
                    throw new RuntimeException("Failed to parse Resource: " + name + " - cluster part specified multiple times");
                }
                if (pat.endsWith("*")) {
                    spec.clusterName = pat.substring(pat.length() - 1);
                    spec.clusterStartsWith = true;
                } else {
                    spec.clusterName = pat;
                }
                continue;
            }

            if (spec.resourceName != null) {
                throw new RuntimeException("Failed to parse Resource: " + name + " - resource part specified multiple times");
            }

            if (type.equals("topic")) {
                spec.resourceType = ResourceType.Topic;
            } else if (type.equals("group")) {
                spec.resourceType = ResourceType.Group;
            } else if (type.equals("cluster")) {
                spec.resourceType = ResourceType.Cluster;
            } else {
                throw new RuntimeException("Failed to parse Resource: " + name + " - unsupported segment type: " + subSpec[0]);
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
