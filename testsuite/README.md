# Testsuite

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Annotation-Based Configuration](#annotation-based-configuration)
- [Test Infrastructure Components](#test-infrastructure-components)
- [Writing Tests](#writing-tests)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)

## Overview

The testsuite has been refactored from multiple separate Maven modules into a single unified module with a modern, annotation-based test infrastructure.
Key improvements include:

The `testsuite` module contains all integration tests for strimzi-kafka-oauth library.
It utilizes `test-containers` to run all the required infrastructure as Docker containers.
Nowadays authorization servers uses pure `test-containers` implementation.
However, for Kafka we use Strimzi's `test-container` implementation where we just copy latest built oauth jars.

The main advantages are:
- **Declarative Configuration**: Use `@OAuthEnvironment` and `@KafkaConfig` annotations to setup testing environment
- **Automatic Container Management**: Testcontainers handles all Docker container lifecycle
- **Automatic Log Collection**: Container and test logs are automatically saved for all tests at `testsuite/target/test-logs`
- **Per-Method Configuration**: Kafka can be reconfigured between test methods
- **JUnit 5**: Modern test framework with better extension support
- **Single Module**: All tests in one place with clear package organization

## Module Structure

```
testsuite/
├── src/main/java/                    # Test infrastructure (compile scope)
│   └── io/strimzi/oauth/testsuite/
│       ├── clients/                   # Kafka client utilities
│       ├── common/                    # Common test utilities
│       ├── environment/               # @OAuthEnvironment extension with helper classes
│       ├── logging/                   # Log collection from tests and containers
│       ├── metrics/                   # Metrics utilities
│       ├── server/                    # Mock OAuth server implementation
│       └── utils/                     # Helper utilities
├── src/test/java/                     # Integration tests (test scope)
│   └── io/strimzi/oauth/testsuite/
│       ├── component/                 # Component tests (more unit-like tests that requires more oauth modules together)
│       ├── hydra/                     # Hydra auth server tests
│       ├── keycloak/                  # Keycloak tests
│       │   ├── auth/                  # Authentication tests
│       │   ├── authz/                 # Authorization tests
│       │   └── errors/                # Error handling tests
│       └── mockoauth/                 # Mock OAuth server tests
└── docker/                            # Docker resources (certificates, etc.)
```

## Quick Start

### Basic Test Example

```java
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(
        realm = "demo",
        oauthProperties = {
            "oauth.client.id=kafka",
            "oauth.fallback.username.claim=client_id"
        }
    )
)
public class MyTest {
    
    OAuthEnvironmentExtension env;  // Auto-injected via @OAuthEnvironment
    
    @Test
    void testBasicAuthentication() throws Exception {
        String bootstrap = env.getBootstrapServers();
        
        Map<String, String> oauthConfig = Map.of(
            ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, 
            "http://" + env.getKeycloakHostPort() + "/realms/demo/protocol/openid-connect/token",
            ClientConfig.OAUTH_CLIENT_ID, "my-client",
            ClientConfig.OAUTH_CLIENT_SECRET, "my-secret"
        );
        
        produceAndConsumeOAuthBearer(bootstrap, oauthConfig, "my-topic", "Hello");
    }
}
```

## Annotation-Based Configuration

### @OAuthEnvironment

The `@OAuthEnvironment` annotation is the main entry point for configuring your test environment.

```java
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,  // or MOCK_OAUTH, HYDRA
    kafka = @KafkaConfig(...)           // Optional Kafka configuration
)
```

**Parameters:**
- `authServer`: Which OAuth server to start (KEYCLOAK, MOCK_OAUTH, HYDRA)
- `kafka`: Kafka cluster configuration (optional as can be on test level)

### @KafkaConfig

The `@KafkaConfig` annotation configures the Kafka cluster. It can be used at:
- **Class level**: Default configuration for all tests
- **Method level**: Override configuration for specific test methods

```java
@KafkaConfig(
    authenticationType = AuthenticationType.OAUTH_BEARER, // Authentication type (default: OAUTH_BEARER)
    realm = "kafka-authz",              // Keycloak realm to use (default: "kafka-authz")
    clientId = "kafka",                 // OAuth client ID for inter-broker auth (default: "kafka")
    clientSecret = "kafka-secret",      // OAuth client secret (default: "kafka-secret")
    usernameClaim = "preferred_username", // OAuth username claim (default: "preferred_username")
    enabled = true,                     // Whether to start Kafka (default: true)
    setupAcls = true,                   // Setup ACLs for authz tests (default: false)
    metrics = true,                     // Enable Prometheus metrics (default: false)
    initEndpoints = true,               // Initialize MockOAuth endpoints (default: true)
    oauthProperties = {                 // Additional OAuth JAAS properties for Kafka broker
        "oauth.check.audience=true"
    },
    kafkaProperties = {                 // Additional Kafka broker properties
        "authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer"
    },
    scramUsers = {                      // SCRAM users to provision (format: "user:password")
        "alice:alice-secret"
    }
)
```

### Per-Method Configuration

You can override Kafka configuration for individual test methods:

```java
@OAuthEnvironment(
    authServer = AuthServer.KEYCLOAK,
    kafka = @KafkaConfig(realm = "demo")  // Default config
)
public class MyTest {
    
    OAuthEnvironmentExtension env;
    
    @Test
    void testWithDefaultConfig() {
        // Uses realm "demo"
    }
    
    @KafkaConfig(
        realm = "kafka-authz",
        oauthProperties = {"oauth.check.audience=true"}
    )
    @Test
    void testWithAudienceCheck() {
        // Kafka is restarted with new configuration
        // Uses realm "kafka-authz" with audience checking
    }
}
```

**Note**: When configuration changes between test methods, Kafka is automatically stopped and restarted with the new configuration.

### TestLogCollector

Automatically collects container logs.
The fully qualified class name is converted to a directory path (dots become `/`) without `io.strimzi.oauth.testsuite` prefix.
Logs are organized as:
```
target/test-logs/<CLASS_DIRECTORY>/<CLASS_NAME>/test.log                 # test log from JUnit executor
target/test-logs/<CLASS_DIRECTORY>/<CLASS_NAME>/containers/              # always-collected container logs
target/test-logs/<CLASS_DIRECTORY>/<CLASS_NAME>/failures/{test-method}/  # point-in-time snapshots on test failure
```

Each container's logs are saved in a separate file named after the first label attached to container (name is usually just a hash).

## Running Tests

### Run All Tests

```bash
mvn clean install -f testsuite
```

### Run Specific Test Class

```bash
mvn verify -f testsuite -Dit.test=AudienceIT
```

### Run Tests by Package

```bash
# All Keycloak auth tests
mvn verify -f testsuite -Dit.test="io.strimzi.oauth.testsuite.keycloak.auth.*"

# All authorization tests
mvn verify -f testsuite -Dit.test="io.strimzi.oauth.testsuite.keycloak.authz.*"

# All MockOAuth tests
mvn verify -f testsuite -Dit.test="io.strimzi.oauth.testsuite.mockoauth.*"
```

### Run Tests by Tag

```bash
# Run only JWT tests
mvn verify -f testsuite -Dgroups="jwt"

# Run only metrics tests
mvn verify -f testsuite -Dgroups="metrics"
```

### Run with Different Kafka Version

```bash
mvn clean install -f testsuite -Pkafka-3_9_1
```

Available profiles: `kafka-3_2_3`, `kafka-3_3_2`, `kafka-3_4_0`, `kafka-3_5_0`, `kafka-3_5_2`, `kafka-3_6_1`, `kafka-3_6_2`, `kafka-3_7_1`, `kafka-3_8_1`, `kafka-3_9_1`, `kafka-4_0_0`, `kafka-4_1_0`

### Run with Custom Kafka Image

```bash
mvn verify -f testsuite -Dkafka.docker.image=quay.io/strimzi/kafka:0.48.0-kafka-4.1.0
```