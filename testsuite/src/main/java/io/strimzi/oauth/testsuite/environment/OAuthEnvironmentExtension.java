/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import io.strimzi.oauth.testsuite.common.ContainerSource;
import io.strimzi.oauth.testsuite.common.ContainerLogCollectorExtension;
import io.strimzi.oauth.testsuite.metrics.TestMetricsReporter;
import io.strimzi.test.container.OAuthKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.environment.TestContainerFactory.CONTAINER_LABEL_KEY;
import static io.strimzi.oauth.testsuite.utils.TestUtil.waitForCondition;

/**
 * JUnit Jupiter extension that manages the full OAuth test environment lifecycle.
 *
 * <p>This extension is activated by the {@link OAuthEnvironment} annotation and handles:
 * <ul>
 *   <li>Starting the auth server (MockOAuth, Keycloak, or Hydra)</li>
 *   <li>Starting and configuring the Kafka container with the correct listener preset</li>
 *   <li>Setting up ACLs for authorization tests</li>
 *   <li>Initializing MockOAuth endpoints</li>
 *   <li>Injecting itself into test instance fields of type {@code OAuthEnvironmentExtension}</li>
 *   <li>Providing container references for log collection</li>
 *   <li>Tearing down all containers after tests complete</li>
 * </ul>
 */
public class OAuthEnvironmentExtension implements BeforeAllCallback, AfterAllCallback, ContainerSource {

    private static final Logger log = LoggerFactory.getLogger(OAuthEnvironmentExtension.class);

    private Network network;
    private GenericContainer<?> authServerContainer;
    private OAuthKafkaContainer kafka;
    private final List<GenericContainer<?>> allContainers = new ArrayList<>();

    // Hydra-specific containers
    private GenericContainer<?> hydra;
    private GenericContainer<?> hydraImport;
    private GenericContainer<?> hydraJwt;
    private GenericContainer<?> hydraJwtImport;

    // Keycloak-specific
    private GenericContainer<?> keycloak;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        OAuthEnvironment config = context.getRequiredTestClass().getAnnotation(OAuthEnvironment.class);
        if (config == null) {
            throw new IllegalStateException("@OAuthEnvironment annotation not found on test class " + context.getRequiredTestClass().getName());
        }

        network = Network.newNetwork();

        // 1. Start auth server
        startAuthServer(config.authServer());

        // 2. Start Kafka if needed
        if (config.kafka().preset() != KafkaPreset.NONE) {
            startKafka(config);
        }

        // 3. Store self in ExtensionContext for TestLogCollectorExtension to find
        context.getStore(ExtensionContext.Namespace.GLOBAL)
               .put(ContainerLogCollectorExtension.CONTAINER_SOURCE_KEY, this);

        // 4. Inject into test instance fields of type OAuthEnvironmentExtension
        context.getTestInstance().ifPresent(this::injectIntoTestInstance);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (kafka != null) {
            kafka.stop();
        }
        if (hydraJwtImport != null) {
            hydraJwtImport.stop();
        }
        if (hydraJwt != null) {
            hydraJwt.stop();
        }
        if (hydraImport != null) {
            hydraImport.stop();
        }
        if (hydra != null) {
            hydra.stop();
        }
        if (keycloak != null) {
            keycloak.stop();
        }
        if (authServerContainer != null && authServerContainer != keycloak && authServerContainer != hydra) {
            authServerContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Override
    public List<GenericContainer<?>> getContainers() {
        return new ArrayList<>(allContainers);
    }

    /**
     * Get the Kafka container.
     *
     * @return The OAuthKafkaContainer
     */
    public OAuthKafkaContainer getKafka() {
        return kafka;
    }

    /**
     * Get the Keycloak host:port for test client connections.
     *
     * @return The host:port string for the Keycloak container
     */
    public String getKeycloakHostPort() {
        if (keycloak == null) {
            throw new IllegalStateException("Keycloak is not started. Use authServer = AuthServer.KEYCLOAK");
        }
        return keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    /**
     * Get the token endpoint URI for the kafka-authz realm.
     *
     * @return The token endpoint URI string
     */
    public String getTokenEndpointUri() {
        return "http://" + getKeycloakHostPort() + "/realms/kafka-authz/protocol/openid-connect/token";
    }

    /**
     * Get the Keycloak container.
     *
     * @return The Keycloak GenericContainer
     */
    public GenericContainer<?> getKeycloak() {
        return keycloak;
    }

    /**
     * Get the MockOAuth container (same as authServerContainer when authServer=MOCK_OAUTH).
     *
     * @return The MockOAuth GenericContainer
     */
    public GenericContainer<?> getMockoauth() {
        return authServerContainer;
    }

    /**
     * Set mock OAuth endpoints to functional modes (MODE_200).
     * Call this after start with initEndpoints=false once any pre-test metric checks are done.
     */
    public void initEndpoints() {
        try {
            MockOAuthAdmin.changeAuthServerMode("jwks", "MODE_200");
            MockOAuthAdmin.changeAuthServerMode("token", "MODE_200");
            MockOAuthAdmin.changeAuthServerMode("introspect", "MODE_200");
            MockOAuthAdmin.changeAuthServerMode("userinfo", "MODE_200");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize mock OAuth endpoints", e);
        }
    }

    /**
     * Wait for ACLs to be set up and visible in the Kafka broker.
     *
     * @throws Exception If an error occurs or the timeout is reached
     */
    public void waitForACLs() throws Exception {
        String plainListener = "localhost:9100";

        waitForCondition(() -> {
            Properties adminProps = new Properties();
            adminProps.setProperty("security.protocol", "SASL_PLAINTEXT");
            adminProps.setProperty("sasl.mechanism", "PLAIN");
            adminProps.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"admin-password\" ;");
            adminProps.setProperty("bootstrap.servers", plainListener);

            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                try {
                    Collection<AclBinding> result = adminClient.describeAcls(new AclBindingFilter(ResourcePatternFilter.ANY,
                            new AccessControlEntryFilter("User:alice", null, AclOperation.IDEMPOTENT_WRITE, AclPermissionType.ALLOW))).values().get();
                    for (AclBinding acl : result) {
                        if (AclOperation.IDEMPOTENT_WRITE.equals(acl.entry().operation())) {
                            return true;
                        }
                    }
                    return false;
                } catch (Throwable e) {
                    throw new RuntimeException("ACLs for User:alice could not be retrieved: ", e);
                }
            }
        }, 2000, 210);
    }

    private void startAuthServer(AuthServer authServer) {
        switch (authServer) {
            case MOCK_OAUTH:
                authServerContainer = TestContainerFactory.createMockOAuth(network);
                authServerContainer.start();
                TestContainerFactory.publishMockOAuthPorts(authServerContainer);
                allContainers.add(authServerContainer);
                break;

            case KEYCLOAK:
                keycloak = TestContainerFactory.createKeycloak(network);
                keycloak.start();
                TestContainerFactory.publishKeycloakPort(keycloak);
                authServerContainer = keycloak;
                allContainers.add(keycloak);
                break;

            case HYDRA:
                startHydra();
                break;

            case NONE:
                break;
        }
    }

    @SuppressWarnings("resource")
    private void startHydra() {
        // Start Hydra (opaque token strategy)
        hydra = new GenericContainer<>("oryd/hydra:v2.3.0")
                .withLabel(CONTAINER_LABEL_KEY, "hydra")
                .withExposedPorts(4444, 4445)
                .withNetwork(network)
                .withNetworkAliases("hydra")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("target/hydra/certs").toAbsolutePath().toString()),
                        "/tmp/certs")
                .withCommand("serve all")
                .withEnv("DSN", "memory")
                .withEnv("URLS_SELF_ISSUER", "https://hydra:4444/")
                .withEnv("URLS_CONSENT", "http://hydra:9020/consent")
                .withEnv("URLS_LOGIN", "http://hydra:9020/login")
                .withEnv("SERVE_PUBLIC_TLS_ENABLED", "true")
                .withEnv("SERVE_ADMIN_TLS_ENABLED", "true")
                .withEnv("SERVE_PUBLIC_TLS_KEY_PATH", "/tmp/certs/hydra.key")
                .withEnv("SERVE_ADMIN_TLS_KEY_PATH", "/tmp/certs/hydra.key")
                .withEnv("SERVE_PUBLIC_TLS_CERT_PATH", "/tmp/certs/hydra.crt")
                .withEnv("SERVE_ADMIN_TLS_CERT_PATH", "/tmp/certs/hydra.crt")
                .withEnv("SERVE_PUBLIC_PORT", "4444")
                .withEnv("SERVE_ADMIN_PORT", "4445")
                .withEnv("SECRETS_SYSTEM", "thisisaglobalsecret")
                .withEnv("LOG_LEVEL", "debug")
                .waitingFor(Wait.forLogMessage(".*Setting up http server on :4444.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        hydra.start();
        System.setProperty("hydra.host", hydra.getHost());
        System.setProperty("hydra.port", String.valueOf(hydra.getMappedPort(4444)));
        allContainers.add(hydra);

        // Run hydra-import init container
        hydraImport = new GenericContainer<>("testsuite/hydra-import:latest")
                .withNetwork(network)
                .withNetworkAliases("hydra-import")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("docker/hydra-import/scripts").toAbsolutePath().toString()),
                        "/hydra")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("target/hydra-import/certs").toAbsolutePath().toString()),
                        "/hydra/certs")
                .withEnv("HYDRA_URI", "https://hydra:4445/admin/clients")
                .withEnv("SERVE_ADMIN_PORT", "4445")
                .withCommand("/bin/bash", "-c", "cd hydra && ./start.sh")
                .waitingFor(Wait.forLogMessage(".*Hydra import complete.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        hydraImport.start();
        allContainers.add(hydraImport);

        // Start Hydra JWT (JWT token strategy)
        hydraJwt = new GenericContainer<>("oryd/hydra:v2.3.0")
                .withExposedPorts(4454, 4455)
                .withNetwork(network)
                .withNetworkAliases("hydra-jwt")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("target/hydra/certs").toAbsolutePath().toString()),
                        "/tmp/certs")
                .withCommand("serve all")
                .withEnv("DSN", "memory")
                .withEnv("URLS_SELF_ISSUER", "https://hydra-jwt:4454/")
                .withEnv("URLS_CONSENT", "http://hydra-jwt:9120/consent")
                .withEnv("URLS_LOGIN", "http://hydra-jwt:9120/login")
                .withEnv("SERVE_PUBLIC_TLS_ENABLED", "true")
                .withEnv("SERVE_ADMIN_TLS_ENABLED", "true")
                .withEnv("SERVE_PUBLIC_TLS_KEY_PATH", "/tmp/certs/hydra-jwt.key")
                .withEnv("SERVE_ADMIN_TLS_KEY_PATH", "/tmp/certs/hydra-jwt.key")
                .withEnv("SERVE_PUBLIC_TLS_CERT_PATH", "/tmp/certs/hydra-jwt.crt")
                .withEnv("SERVE_ADMIN_TLS_CERT_PATH", "/tmp/certs/hydra-jwt.crt")
                .withEnv("SERVE_PUBLIC_PORT", "4454")
                .withEnv("SERVE_ADMIN_PORT", "4455")
                .withEnv("STRATEGIES_ACCESS_TOKEN", "jwt")
                .withEnv("SECRETS_SYSTEM", "thisisaglobalsecret")
                .withEnv("LOG_LEVEL", "debug")
                .waitingFor(Wait.forLogMessage(".*Setting up http server on :4454.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        hydraJwt.start();
        System.setProperty("hydra.jwt.host", hydraJwt.getHost());
        System.setProperty("hydra.jwt.port", String.valueOf(hydraJwt.getMappedPort(4454)));
        allContainers.add(hydraJwt);

        // Run hydra-jwt-import init container
        hydraJwtImport = new GenericContainer<>("testsuite/hydra-import:latest")
                .withNetwork(network)
                .withNetworkAliases("hydra-jwt-import")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("docker/hydra-import/scripts").toAbsolutePath().toString()),
                        "/hydra")
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("target/hydra-import/certs").toAbsolutePath().toString()),
                        "/hydra/certs")
                .withEnv("HYDRA_URI", "https://hydra-jwt:4455/admin/clients")
                .withEnv("SERVE_ADMIN_PORT", "4455")
                .withCommand("/bin/bash", "-c", "cd hydra && ./start.sh")
                .waitingFor(Wait.forLogMessage(".*Hydra import complete.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        hydraJwtImport.start();
        allContainers.add(hydraJwtImport);

        authServerContainer = hydra;
    }

    private void startKafka(OAuthEnvironment config) {
        KafkaConfig kafkaConfig = config.kafka();
        Map<String, String> kafkaConfigMap = KafkaPresetConfig.getConfig(kafkaConfig.preset());

        kafka = TestContainerFactory.createKafkaBase(network);
        kafka.withMetrics("/opt/kafka/config/strimzi/metrics-config.yml");
        kafka.withKafkaConfigurationMap(kafkaConfigMap);

        // Configure environment-specific settings
        configureKafkaEnv(config.authServer(), kafkaConfig);

        // Configure ports based on preset
        configureKafkaPorts(kafkaConfig.preset());

        kafka.waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));
        kafka.start();
        allContainers.add(kafka);

        // Post-start actions
        if (kafkaConfig.setupAcls()) {
            setupACLs();
        }

        if (config.authServer() == AuthServer.MOCK_OAUTH && kafkaConfig.initEndpoints()) {
            initEndpoints();
        }
    }

    private void configureKafkaEnv(AuthServer authServer, KafkaConfig kafkaConfig) {
        switch (authServer) {
            case MOCK_OAUTH:
                kafka.withCopyDirToContainer(Path.of("target/kafka/certs").toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");
                kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "10");
                kafka.addEnv("OAUTH_READ_TIMEOUT_SECONDS", "10");
                kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
                kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter");
                break;

            case KEYCLOAK:
                if (kafkaConfig.preset() == KafkaPreset.KEYCLOAK_AUTH) {
                    kafka.withCopyToContainer(
                            MountableFile.forHostPath(Path.of("target/classes").toAbsolutePath().toString()),
                            "/opt/kafka/libs/strimzi/reporters");
                    kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
                    kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
                    kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter," + TestMetricsReporter.class.getName());
                    kafka.addEnv("CLASSPATH", "/opt/kafka/libs/strimzi/reporters");
                } else if (kafkaConfig.preset() == KafkaPreset.KEYCLOAK_AUTHZ) {
                    kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
                    kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");
                    kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
                    kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter");
                    kafka.addEnv("KEYCLOAK_HOST", "keycloak");
                } else if (kafkaConfig.preset() == KafkaPreset.KEYCLOAK_ERRORS) {
                    kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
                    kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");
                }
                break;

            case HYDRA:
                kafka.withCopyDirToContainer(Path.of("target/kafka/certs").toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
                kafka.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");
                kafka.addEnv("HYDRA_HOST", "hydra");
                break;

            default:
                break;
        }
    }

    private void configureKafkaPorts(KafkaPreset preset) {
        switch (preset) {
            case MOCK_OAUTH:
                kafka.addFixedExposedPort(9091, 9091);
                kafka.addFixedExposedPort(9092, 9092);
                kafka.addFixedExposedPort(9093, 9093);
                kafka.addFixedExposedPort(9094, 9094);
                kafka.addFixedExposedPort(9095, 9095);
                kafka.addFixedExposedPort(9096, 9096);
                kafka.addFixedExposedPort(9097, 9097);
                kafka.addFixedExposedPort(9098, 9098);
                kafka.addFixedExposedPort(9099, 9099);
                kafka.addFixedExposedPort(9404, 9404);
                break;

            case KEYCLOAK_AUTH:
                kafka.addFixedExposedPort(9091, 9091);
                kafka.addFixedExposedPort(9092, 9092);
                kafka.addFixedExposedPort(9093, 9093);
                kafka.addFixedExposedPort(9094, 9094);
                kafka.addFixedExposedPort(9095, 9095);
                kafka.addFixedExposedPort(9096, 9096);
                kafka.addFixedExposedPort(9097, 9097);
                kafka.addFixedExposedPort(9098, 9098);
                kafka.addFixedExposedPort(9099, 9099);
                kafka.addFixedExposedPort(9100, 9100);
                kafka.addFixedExposedPort(9101, 9101);
                kafka.addFixedExposedPort(9102, 9102);
                kafka.addFixedExposedPort(9103, 9103);
                kafka.addFixedExposedPort(9104, 9104);
                kafka.addFixedExposedPort(9404, 9404);
                break;

            case KEYCLOAK_AUTHZ:
                kafka.addFixedExposedPort(9091, 9091);
                kafka.addFixedExposedPort(9092, 9092);
                kafka.addFixedExposedPort(9093, 9093);
                kafka.addFixedExposedPort(9094, 9094);
                kafka.addFixedExposedPort(9095, 9095);
                kafka.addFixedExposedPort(9096, 9096);
                kafka.addFixedExposedPort(9100, 9100);
                kafka.addFixedExposedPort(9101, 9101);
                kafka.addFixedExposedPort(9404, 9404);
                break;

            case KEYCLOAK_ERRORS:
                kafka.addFixedExposedPort(9091, 9091);
                kafka.addFixedExposedPort(9201, 9201);
                kafka.addFixedExposedPort(9202, 9202);
                kafka.addFixedExposedPort(9203, 9203);
                kafka.addFixedExposedPort(9204, 9204);
                kafka.addFixedExposedPort(9205, 9205);
                kafka.addFixedExposedPort(9206, 9206);
                kafka.addFixedExposedPort(9207, 9207);
                kafka.addFixedExposedPort(9208, 9208);
                break;

            case HYDRA:
                kafka.addFixedExposedPort(9091, 9091);
                kafka.addFixedExposedPort(9092, 9092);
                kafka.addFixedExposedPort(9093, 9093);
                break;

            default:
                break;
        }
    }

    /**
     * Run the ACL setup commands for authorization tests.
     */
    private void setupACLs() {
        try {
            String adminConfig =
                    "security.protocol=SASL_PLAINTEXT\n" +
                    "sasl.mechanism=PLAIN\n" +
                    "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"admin-password\" ;\n" +
                    "default.api.timeout.ms=210000\n";

            execAndCheck("write admin.properties",
                    "cat > /tmp/admin.properties <<'PROPEOF'\n" + adminConfig + "PROPEOF");

            execAndCheck("kafka-acls bobby topic",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation Describe --operation Create --operation Write " +
                    "--topic KeycloakAuthorizationTest-multiSaslTest-plain");

            execAndCheck("kafka-acls bobby cluster",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation IdempotentWrite --cluster kafka-cluster");

            execAndCheck("kafka-acls alice topic",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:alice --operation Describe --operation Create --operation Write " +
                    "--topic KeycloakAuthorizationTest-multiSaslTest-scram");

            execAndCheck("kafka-acls alice cluster",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:alice --operation IdempotentWrite --cluster kafka-cluster");

            log.info("ACL setup completed successfully");
        } catch (Exception e) {
            log.error("Failed to set up ACLs", e);
            throw new RuntimeException("ACL setup failed", e);
        }
    }

    private void execAndCheck(String description, String command) throws Exception {
        Container.ExecResult result = kafka.execInContainer("bash", "-c", command);
        if (result.getExitCode() != 0) {
            log.error("{} failed (exit code {}): stdout={}, stderr={}", description, result.getExitCode(), result.getStdout(), result.getStderr());
            throw new RuntimeException(description + " failed with exit code " + result.getExitCode() + ": " + result.getStderr());
        }
        log.info("{} succeeded: {}", description, result.getStdout().trim());
    }

    /**
     * Inject this extension instance into test instance fields of type {@code OAuthEnvironmentExtension}.
     */
    private void injectIntoTestInstance(Object testInstance) {
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (OAuthEnvironmentExtension.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        field.set(testInstance, this);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to inject OAuthEnvironmentExtension into field " + field.getName(), e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
