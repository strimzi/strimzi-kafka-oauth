# Using Keycloak Authorization Services with Strimzi Kafka OAuth

## Overview

Strimzi Kafka OAuth can integrate with Keycloak Authorization Services to provide fine-grained access control for Kafka clients. When Keycloak authorization is enabled on the Kafka broker, the broker evaluates the permissions defined in Keycloak to determine whether a client is allowed to perform specific Kafka operations.

Keycloak Authorization Services use resources, scopes, policies, and permissions to model what an authenticated user or application can do. The Strimzi Kafka OAuth `KeycloakAuthorizer` retrieves authorization grants from Keycloak at runtime and uses those grants to authorize Kafka requests.

This document describes how Kafka authorization concepts relate to Keycloak Authorization Services, how permissions are mapped, and how to model Kafka resources and operations in Keycloak. It also provides high-level guidance on the example Keycloak realm included with the project.

## Kafka and Keycloak authorization models

Kafka and Keycloak use different abstractions to represent permissions. When the KeycloakAuthorizer is enabled on the Kafka broker, these models intersect through the permissions that Keycloak Authorization Services issue to authenticated users or applications.

### Kafka authorization model

Kafka authorizes client requests based on:

- **Resource type** (for example: Topic, Group, Cluster)
- **Resource name** (for example: a specific topic name)
- **Operation** (for example: Read, Write, Create, Alter)
- **Permission type** (Allow or Deny)

Authorization decisions are made for each request, based on whether the user has the required permission for the targeted resource.

### Keycloak authorization model

Keycloak Authorization Services represent permissions using:

- **Resources** – protected entities, identified by a name or URI
- **Scopes** – operations that can be performed on resources
- **Policies** – rules that evaluate access based on conditions (roles, groups, user attributes, etc.)
- **Permissions** – associations between resources, scopes, and policies

Access tokens issued by Keycloak contain grants describing the permissions available to the authenticated principal.

### How the models align

When the broker receives an OAuth access token, the KeycloakAuthorizer retrieves the authorization grants from Keycloak and uses them to authorize Kafka operations. The mapping is as follows:

- Kafka **resource types** map to Keycloak **resources**
- Kafka **operations** map to Keycloak **scopes**
- Keycloak **permissions** express which operations are allowed on which Kafka resources
- Keycloak **policies** determine the conditions under which permissions are granted

The following sections describe how to map Kafka resources and operations to Keycloak Authorization Services.

## Mapping authorization models

To use Keycloak Authorization Services for Kafka authorization, you model Kafka resources and operations using Keycloak resources, scopes, and permissions. The goal is to represent Kafka operations in a way that allows Keycloak to issue permissions that the KeycloakAuthorizer can evaluate.

### Mapping Kafka resources to Keycloak resources

Kafka defines several resource types, including:

- **Topic**
- **Group**
- **Cluster**

These resource types can be represented as Keycloak resources. A common approach is to define a Keycloak resource for each Kafka resource type, for example:

- `Topic:*`
- `Group:*`
- `kafka-cluster:*`

You can also define more granular resources and combine patterns for a Keycloak resource when defining permissions, for example:

- `Topic:orders`
- `Group:analytics`
- `kafka-cluster:my-cluster,Topic:*`

Keycloak allows flexible resource naming, so you can choose patterns that best reflect your environment.

### Mapping Kafka operations to Keycloak scopes

Kafka operations such as `Read`, `Write`, and `Create` can be modeled as Keycloak scopes.  

Authorization scopes should contain the following Kafka permissions regardless of the resource type:

* `Create`
* `Write`
* `Read`
* `Delete`
* `Describe`
* `Alter`
* `DescribeConfigs`
* `AlterConfigs`
* `ClusterAction`
* `IdempotentWrite`

Defining one scope per Kafka operation allows Keycloak to express fine-grained permissions for each operation.

### Defining permissions in Keycloak

A permission in Keycloak links:

- One or more **resources**
- One or more **scopes**
- One or more **policies**

Example:

- Resource: `Topic:orders`
- Scope: `Read`
- Policy: `role:user`

This permission states that users with the `user` role may read the `orders` topic.

### Using policies to express access rules

Keycloak Authorization Services support many policy types, such as:

- Role-based policies
- Group-based policies
- Attribute-based policies
- Aggregated policies

Kafka does not evaluate Keycloak policies.
Keycloak evaluates them and embeds the resulting permissions into grants, which the KeycloakAuthorizer uses to authorize Kafka requests.

### Uploading scopes and defining initial permissions

To simplify initial setup, you can upload a set of predefined scopes that correspond to common Kafka operations. The example Keycloak realm included with this project demonstrates how to initialize these scopes and permissions.

This approach provides:

- A consistent set of Kafka-related scopes
- A base set of permissions that can be adapted to your environment
- A starting point for modeling resource patterns and per-resource permissions

The following section provides a summary of the permissions commonly required for Kafka operations.

## Permissions for common Kafka operations

Kafka clients perform operations on resources such as topics, consumer groups, and the Kafka cluster.  
When Keycloak Authorization Services are used, these operations must be represented as scopes and associated with permissions in Keycloak.

This section summarizes the operations commonly used by Kafka clients and the corresponding scopes that can be associated in Keycloak.

### Topic operations

Typical Kafka topic operations include:

- `Read` – consume records from a topic
- `Write` – produce records to a topic
- `Create` – create a new topic
- `Delete` – delete a topic
- `AlterConfigs` – update topic configuration
- `Describe` – view topic metadata

In Keycloak, you assign these operations as scopes to a topic resource, for example:

- Resource: `Topic:orders`
- Scopes: `Read`, `Write`, `Describe`

### Consumer group operations

Consumer group–related operations include:

- `Read` – join a consumer group
- `Describe` – view group information
- `Delete` – delete a consumer group

In Keycloak, these operations can be modeled with a `Group:*` resource and appropriate scopes.

### Cluster-level operations

Cluster-level administration operations can include:

- `AlterConfigs`
- `DescribeConfigs`
- `Describe`
- `Create`
- `IdempotentWrite`

These operations typically apply to all resources in the cluster and can be modeled using a `kafka-cluster:*` resource.

### Assigning permissions

In Keycloak, a permission links a resource, scope, and policy. Examples:

- Allow producers to write to any topic:
  - Resource: `Topic:*`
  - Scope: `Write`
  - Policy: `role:producer`

- Allow consumers to read and describe topics:
  - Resource: `Topic:*`
  - Scopes: `Read`, `Describe`
  - Policy: `role:consumer`

- Allow administrators to create or delete topics:
  - Resource: `kafka-cluster:*`
  - Scopes: `Create`, `Delete`
  - Policy: `role:admin`

The exact permissions depend on your deployment and access control requirements. The example Keycloak realm in this repository shows one approach to defining these mappings.

## Example setup

This repository includes an example Keycloak realm and Kafka configuration that demonstrate how to use Keycloak Authorization Services with the Strimzi Kafka OAuth libraries.

The example shows:

- How Kafka resources and operations can be represented as Keycloak resources and scopes
- How permissions and policies are defined in Keycloak
- How the `KeycloakAuthorizer` evaluates grants issued by Keycloak
- A sample configuration of a Kafka cluster that uses Keycloak for authentication and authorization

These examples are located at [examples/kubernetes](.).
To run the example on Kubernetes, see the [deployment instructions](./README.md).   

The example configuration is specific to Strimzi and Kubernetes, but the authorization model and concepts described in this document apply to any environment that integrates Kafka with Keycloak Authorization Services.