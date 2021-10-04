/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import org.junit.Assert;
import org.junit.Test;

public class GrantsParsingTest {

    @Test
    public void testScopesSpec() {
        Assert.assertEquals(ScopesSpec.AuthzScope.CREATE, ScopesSpec.AuthzScope.of("Create"));
        Assert.assertEquals(ScopesSpec.AuthzScope.READ, ScopesSpec.AuthzScope.of("Read"));
        Assert.assertEquals(ScopesSpec.AuthzScope.WRITE, ScopesSpec.AuthzScope.of("Write"));
        Assert.assertEquals(ScopesSpec.AuthzScope.DELETE, ScopesSpec.AuthzScope.of("Delete"));
        Assert.assertEquals(ScopesSpec.AuthzScope.ALTER, ScopesSpec.AuthzScope.of("Alter"));
        Assert.assertEquals(ScopesSpec.AuthzScope.DESCRIBE, ScopesSpec.AuthzScope.of("Describe"));
        Assert.assertEquals(ScopesSpec.AuthzScope.ALTER_CONFIGS, ScopesSpec.AuthzScope.of("AlterConfigs"));
        Assert.assertEquals(ScopesSpec.AuthzScope.DESCRIBE_CONFIGS, ScopesSpec.AuthzScope.of("DescribeConfigs"));
        Assert.assertEquals(ScopesSpec.AuthzScope.CLUSTER_ACTION, ScopesSpec.AuthzScope.of("ClusterAction"));
        Assert.assertEquals(ScopesSpec.AuthzScope.IDEMPOTENT_WRITE, ScopesSpec.AuthzScope.of("IdempotentWrite"));
    }

    @Test
    public void testResourceSpec() {
        ResourceSpec resourceSpec = ResourceSpec.of("Cluster:*");
        Assert.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:*");
        Assert.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:*");
        Assert.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Cluster:kafka-cluster");
        Assert.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assert.assertFalse(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("kafka-cluster", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:a_*");
        Assert.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Topic:messages");
        Assert.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assert.assertFalse(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("messages", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:a_*");
        Assert.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("Group:a_group");
        Assert.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assert.assertFalse(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_group", resourceSpec.getResourceName());
        Assert.assertNull(resourceSpec.getClusterName());

        resourceSpec = ResourceSpec.of("kafka-cluster:my-cluster,Cluster:*");
        Assert.assertEquals(ResourceSpec.ResourceType.CLUSTER, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("", resourceSpec.getResourceName());
        Assert.assertEquals("my-cluster", resourceSpec.getClusterName());
        Assert.assertFalse(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:my-cluster,Group:a_*");
        Assert.assertEquals(ResourceSpec.ResourceType.GROUP, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_", resourceSpec.getResourceName());
        Assert.assertEquals("my-cluster", resourceSpec.getClusterName());
        Assert.assertFalse(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:*,Topic:a_*");
        Assert.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assert.assertTrue(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_", resourceSpec.getResourceName());
        Assert.assertEquals("", resourceSpec.getClusterName());
        Assert.assertTrue(resourceSpec.isClusterStartsWith());

        resourceSpec = ResourceSpec.of("kafka-cluster:prod_*,Topic:a_messages");
        Assert.assertEquals(ResourceSpec.ResourceType.TOPIC, resourceSpec.getResourceType());
        Assert.assertFalse(resourceSpec.isResourceStartsWith());
        Assert.assertEquals("a_messages", resourceSpec.getResourceName());
        Assert.assertEquals("prod_", resourceSpec.getClusterName());
        Assert.assertTrue(resourceSpec.isClusterStartsWith());
    }

}
