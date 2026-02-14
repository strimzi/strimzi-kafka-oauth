/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GrantsParsingTest {

    @Test
    public void testScopesSpec() {
        Assertions.assertEquals(ScopesSpec.AuthzScope.CREATE, ScopesSpec.AuthzScope.of("Create"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.READ, ScopesSpec.AuthzScope.of("Read"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.WRITE, ScopesSpec.AuthzScope.of("Write"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.DELETE, ScopesSpec.AuthzScope.of("Delete"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.ALTER, ScopesSpec.AuthzScope.of("Alter"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.DESCRIBE, ScopesSpec.AuthzScope.of("Describe"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.ALTER_CONFIGS, ScopesSpec.AuthzScope.of("AlterConfigs"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.DESCRIBE_CONFIGS, ScopesSpec.AuthzScope.of("DescribeConfigs"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.CLUSTER_ACTION, ScopesSpec.AuthzScope.of("ClusterAction"));
        Assertions.assertEquals(ScopesSpec.AuthzScope.IDEMPOTENT_WRITE, ScopesSpec.AuthzScope.of("IdempotentWrite"));
    }

    @Test
    public void testResourceSpec() {
        ResourceSpec resourceSpec = ResourceSpec.of("Cluster:*");
        Assertions.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:*");
        Assertions.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:*");
        Assertions.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Cluster:kafka-cluster");
        Assertions.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assertions.assertFalse(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("kafka-cluster", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:a_*");
        Assertions.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:messages");
        Assertions.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assertions.assertFalse(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("messages", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:a_*");
        Assertions.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:a_group");
        Assertions.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assertions.assertFalse(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_group", resourceSpec.getResourceName());
        Assertions.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("kafka-cluster:my-cluster,Cluster:*");
        Assertions.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("", resourceSpec.getResourceName());
        Assertions.assertEquals("my-cluster", resourceSpec.getClusterName());
        Assertions.assertFalse(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:my-cluster,Group:a_*");
        Assertions.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_", resourceSpec.getResourceName());
        Assertions.assertEquals("my-cluster", resourceSpec.getClusterName());
        Assertions.assertFalse(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:*,Topic:a_*");
        Assertions.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assertions.assertTrue(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_", resourceSpec.getResourceName());
        Assertions.assertEquals("", resourceSpec.getClusterName());
        Assertions.assertTrue(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:prod_*,Topic:a_messages");
        Assertions.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assertions.assertFalse(resourceSpec.isResourceStartsWith());
        Assertions.assertEquals("a_messages", resourceSpec.getResourceName());
        Assertions.assertEquals("prod_", resourceSpec.getClusterName());
        Assertions.assertTrue(resourceSpec.isClusterStartsWith());
    }

}
