/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.oauth.testsuite.clients.MockOAuthAdmin;
import io.strimzi.oauth.testsuite.logging.TestLogCollectorExtension;
import io.strimzi.oauth.testsuite.logging.TestLogPaths;
import io.strimzi.test.container.AuthenticationType;
import io.strimzi.test.container.StrimziKafkaCluster;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.logging.TestLogPaths.containersDir;
import static io.strimzi.oauth.testsuite.environment.TestContainerFactory.CONTAINER_LABEL_KEY;
import static io.strimzi.oauth.testsuite.utils.TestUtil.waitForCondition;

/**
 * JUnit Jupiter extension that manages the full OAuth test environment lifecycle.
 *
 * <p>This extension is activated by the {@link OAuthEnvironment} annotation and handles:
 * <ul>
 *   <li>Starting the auth server (MockOAuth, Keycloak, or Hydra)</li>
 *   <li>Starting and configuring a single-listener Kafka cluster using StrimziKafkaCluster</li>
 *   <li>Setting up ACLs for authorization tests</li>
 *   <li>Initializing MockOAuth endpoints</li>
 *   <li>Injecting itself into test instance fields of type {@code OAuthEnvironmentExtension}</li>
 *   <li>Providing container references for log collection</li>
 *   <li>Tearing down all containers after tests complete</li>
 * </ul>
 */
public class OAuthEnvironmentExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(OAuthEnvironmentExtension.class);

    private Network network;
    private GenericContainer<?> authServerContainer;
    private StrimziKafkaCluster kafkaCluster;
    private final List<GenericContainer<?>> allContainers = new ArrayList<>();

    /**
     * Class-level @OAuthEnvironment config, stored for per-method Kafka restarts
     */
    private OAuthEnvironment classConfig;

    /**
     * Key representing the currently running Kafka config, used to detect when restart is needed
     */
    private String currentKafkaConfigKey;

    // Hydra-specific containers
    private GenericContainer<?> hydra;
    private GenericContainer<?> hydraImport;
    private GenericContainer<?> hydraJwt;
    private GenericContainer<?> hydraJwtImport;

    // Keycloak-specific containers
    private GenericContainer<?> keycloak;

    /**
     * Fully-qualified test class name, set in beforeAll() for log path construction
     */
    private String testLogDirPath;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        OAuthEnvironment config = context.getRequiredTestClass()
            .getAnnotation(OAuthEnvironment.class);
        if (config == null) {
            throw new IllegalStateException("@OAuthEnvironment annotation not found on test class " + context.getRequiredTestClass()
                .getName());
        }

        classConfig = config;
        // Compute test log dir and remove io.strimzi.oauth.testsuite from package name
        testLogDirPath = context.getRequiredTestClass()
            .getName()
            .replace("io.strimzi.oauth.testsuite", "");
        network = Network.newNetwork();

        // 1. Start auth server
        startAuthServer(config.authServer());

        // 2. Start Kafka if the KafkaConfig is enabled
        if (config.kafka()
            .enabled()) {
            startKafkaWithConfig(config.kafka(), config.authServer());
            currentKafkaConfigKey = buildConfigKey(config.kafka());
        }

        // 3. Store self in ExtensionContext for TestLogCollectorExtension to find
        context.getStore(ExtensionContext.Namespace.GLOBAL)
            .put(TestLogCollectorExtension.ENVIRONMENT_KEY, this);

        // 4. Inject into test instance fields of type OAuthEnvironmentExtension
        context.getTestInstance()
            .ifPresent(this::injectIntoTestInstance);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        KafkaConfig methodKafkaConfig = testMethod.getAnnotation(KafkaConfig.class);

        // Determine the effective KafkaConfig: method-level overrides class-level
        KafkaConfig effectiveConfig;
        if (methodKafkaConfig != null) {
            effectiveConfig = methodKafkaConfig;
        } else {
            effectiveConfig = classConfig.kafka();
        }

        if (!effectiveConfig.enabled()) {
            // Effective config says no Kafka needed — stop if running
            if (kafkaCluster != null) {
                stopKafka();
            }
            return;
        }

        String newKey = buildConfigKey(effectiveConfig);
        if (!newKey.equals(currentKafkaConfigKey)) {
            log.info("Kafka config changed for test {} — restarting Kafka cluster", testMethod.getName());
            if (kafkaCluster != null) {
                stopKafka();
            }
            startKafkaWithConfig(effectiveConfig, classConfig.authServer());
            currentKafkaConfigKey = newKey;
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        collectAllContainerLogs();

        if (kafkaCluster != null) {
            kafkaCluster.stop();
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

        // Copy test log from target into respective test log location
        copyTestLog();
    }

    /**
     * Get all containers managed by this environment.
     *
     * @return A copy of the list of all containers
     */
    public List<GenericContainer<?>> getContainers() {
        return new ArrayList<>(allContainers);
    }

    /**
     * Get the Kafka cluster.
     *
     * @return The StrimziKafkaCluster
     */
    public StrimziKafkaCluster getKafkaCluster() {
        return kafkaCluster;
    }

    /**
     * Get the first Kafka container (for single-broker clusters).
     * Useful for log inspection and exec operations.
     *
     * @return The first StrimziKafkaContainer (as GenericContainer for compatibility)
     */
    public GenericContainer<?> getKafka() {
        if (kafkaCluster == null) {
            throw new IllegalStateException("Kafka cluster is not started");
        }
        return kafkaCluster.getBrokers()
            .iterator()
            .next();
    }

    /**
     * Get the bootstrap servers string for Kafka clients.
     * Returns just "host:port" format (strips the PLAINTEXT:// protocol prefix).
     *
     * @return The bootstrap servers string
     */
    public String getBootstrapServers() {
        if (kafkaCluster == null) {
            throw new IllegalStateException("Kafka cluster is not started");
        }
        String bs = kafkaCluster.getBootstrapServers();
        // Strip protocol prefix (e.g., "PLAINTEXT://") if present
        int idx = bs.indexOf("://");
        if (idx >= 0) {
            bs = bs.substring(idx + 3);
        }
        return bs;
    }

    /**
     * Get the Prometheus metrics URI for the Kafka broker.
     * Uses the dynamically mapped port for container port 9404.
     *
     * @return The metrics endpoint URI string
     */
    public String getMetricsUri() {
        GenericContainer<?> kafka = getKafka();
        return "http://" + kafka.getHost() + ":" + kafka.getMappedPort(9404) + "/metrics";
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
    public void initMockOAuthEndpoints() {
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
        String bootstrapServers = getBootstrapServers();

        waitForCondition(() -> {
            Properties adminProps = new Properties();
            adminProps.setProperty("security.protocol", "SASL_PLAINTEXT");
            adminProps.setProperty("sasl.mechanism", "PLAIN");
            adminProps.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"admin-password\" ;");
            adminProps.setProperty("bootstrap.servers", bootstrapServers);

            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                try {
                    Collection<AclBinding> result = adminClient.describeAcls(new AclBindingFilter(ResourcePatternFilter.ANY,
                            new AccessControlEntryFilter("User:alice", null, AclOperation.IDEMPOTENT_WRITE, AclPermissionType.ALLOW)))
                        .values()
                        .get();
                    for (AclBinding acl : result) {
                        if (AclOperation.IDEMPOTENT_WRITE.equals(acl.entry()
                            .operation())) {
                            return true;
                        }
                    }
                    return false;
                } catch (Throwable e) {
                    throw new RuntimeException("ACLs for User:alice could not be retrieved: ", e);
                }
            }
        }, 2000, 60);
    }

    private void startAuthServer(AuthServer authServer) {
        switch (authServer) {
            case MOCK_OAUTH:
                authServerContainer = TestContainerFactory.createMockOAuth(network);
                try {
                    authServerContainer.start();
                } catch (Exception e) {
                    dumpContainerLogsOnStartupFailure("mockoauth", authServerContainer);
                    throw e;
                }
                TestContainerFactory.publishMockOAuthPorts(authServerContainer);
                allContainers.add(authServerContainer);
                break;

            case KEYCLOAK:
                keycloak = TestContainerFactory.createKeycloak(network);
                try {
                    keycloak.start();
                } catch (Exception e) {
                    dumpContainerLogsOnStartupFailure("keycloak", keycloak);
                    throw e;
                }
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

    private void startHydra() {
        // Start Hydra (opaque token strategy)
        hydra = TestContainerFactory.createHydra(network);
        try {
            hydra.start();
        } catch (Exception e) {
            dumpContainerLogsOnStartupFailure("hydra", hydra);
            throw e;
        }
        TestContainerFactory.publishHydraPorts(hydra);
        allContainers.add(hydra);

        // Run hydra-import init container
        hydraImport = TestContainerFactory.createHydraImport(network, "https://hydra:4445/admin/clients", "4445");
        try {
            hydraImport.start();
        } catch (Exception e) {
            dumpContainerLogsOnStartupFailure("hydra-import", hydraImport);
            dumpContainerLogsOnStartupFailure("hydra", hydra);
            throw e;
        }
        allContainers.add(hydraImport);

        // Start Hydra JWT (JWT token strategy)
        hydraJwt = TestContainerFactory.createHydraJwt(network);
        try {
            hydraJwt.start();
        } catch (Exception e) {
            dumpContainerLogsOnStartupFailure("hydra-jwt", hydraJwt);
            dumpContainerLogsOnStartupFailure("hydra", hydra);
            dumpContainerLogsOnStartupFailure("hydra-import", hydraImport);
            throw e;
        }
        TestContainerFactory.publishHydraJwtPorts(hydraJwt);
        allContainers.add(hydraJwt);

        // Run hydra-jwt-import init container
        hydraJwtImport = TestContainerFactory.createHydraImport(network, "https://hydra-jwt:4455/admin/clients", "4455");
        hydraJwtImport.withNetworkAliases("hydra-jwt-import");
        try {
            hydraJwtImport.start();
        } catch (Exception e) {
            dumpContainerLogsOnStartupFailure("hydra-jwt-import", hydraJwtImport);
            dumpContainerLogsOnStartupFailure("hydra", hydra);
            dumpContainerLogsOnStartupFailure("hydra-import", hydraImport);
            dumpContainerLogsOnStartupFailure("hydra-jwt", hydraJwt);
            throw e;
        }
        allContainers.add(hydraJwtImport);

        authServerContainer = hydra;
    }

    /**
     * Stop the currently running Kafka cluster and remove its containers from the tracking list.
     */
    private void stopKafka() {
        if (kafkaCluster != null) {
            for (StrimziKafkaContainer node : kafkaCluster.getNodes()) {
                allContainers.remove(node);
            }
            kafkaCluster.stop();
            kafkaCluster = null;
            currentKafkaConfigKey = null;
        }
    }

    /**
     * Build a config key string from a KafkaConfig annotation for comparison purposes.
     * Two KafkaConfig annotations with the same key produce identical Kafka setups.
     */
    private static String buildConfigKey(KafkaConfig config) {
        return config.authenticationType() + "|" +
            config.realm() + "|" +
            config.clientId() + "|" +
            config.clientSecret() + "|" +
            config.usernameClaim() + "|" +
            Arrays.toString(config.oauthProperties()) + "|" +
            Arrays.toString(config.kafkaProperties()) + "|" +
            config.metrics() + "|" +
            config.initEndpoints() + "|" +
            config.setupAcls() + "|" +
            Arrays.toString(config.scramUsers());
    }

    private void startKafkaWithConfig(KafkaConfig kafkaConfig, AuthServer authServer) {

        // Determine the auth server URL for building endpoints
        String authServerUrl = getAuthServerUrl(authServer);

        // Build additional Kafka configuration from annotation
        Map<String, String> additionalConfig = parseProperties(kafkaConfig.kafkaProperties());

        // Always configure SASL only on the PLAINTEXT listener, keeping BROKER1 and CONTROLLER
        // as plain PLAINTEXT (no auth). This avoids inter-broker OAuth failures when the test's
        // realm doesn't have the inter-broker client.
        if (kafkaConfig.authenticationType() == AuthenticationType.OAUTH_BEARER) {
            String jaasConfig = buildOAuthBearerJaasConfig(kafkaConfig, authServerUrl);
            additionalConfig.put("listener.name.plaintext.oauthbearer.sasl.jaas.config", jaasConfig);
            additionalConfig.put("listener.name.plaintext.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
            additionalConfig.put("sasl.enabled.mechanisms", "OAUTHBEARER");
            additionalConfig.put("listener.security.protocol.map",
                "PLAINTEXT:SASL_PLAINTEXT,BROKER1:PLAINTEXT,CONTROLLER:PLAINTEXT");
            additionalConfig.put("principal.builder.class",
                "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");
            additionalConfig.put("offsets.topic.replication.factor", "1");

            ensureAnonymousSuperUser(additionalConfig);
        } else if (kafkaConfig.authenticationType() == AuthenticationType.OAUTH_OVER_PLAIN) {
            String jaasConfig = buildOAuthOverPlainJaasConfig(kafkaConfig, authServerUrl);
            additionalConfig.put("listener.name.plaintext.plain.sasl.jaas.config", jaasConfig);
            additionalConfig.put("listener.name.plaintext.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");
            additionalConfig.put("sasl.enabled.mechanisms", "PLAIN");
            additionalConfig.put("listener.security.protocol.map",
                "PLAINTEXT:SASL_PLAINTEXT,BROKER1:PLAINTEXT,CONTROLLER:PLAINTEXT");
            additionalConfig.put("principal.builder.class",
                "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");
            additionalConfig.put("offsets.topic.replication.factor", "1");

            ensureAnonymousSuperUser(additionalConfig);
        }

        // For NONE auth type with an authorizer, BROKER1 is plain PLAINTEXT so
        // inter-broker connections authenticate as ANONYMOUS. Add to super.users.
        if (kafkaConfig.authenticationType() == AuthenticationType.NONE) {
            ensureAnonymousSuperUser(additionalConfig);
        }

        // Build the StrimziKafkaCluster
        String kafkaImage = System.getProperty("KAFKA_DOCKER_IMAGE");
        StrimziKafkaCluster.StrimziKafkaClusterBuilder builder = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
            .withNumberOfBrokers(1)
            .withAdditionalKafkaConfiguration(additionalConfig)
            .withLogCollection(containersDir(testLogDirPath).getPath() + "/")
            .withContainerCustomizer(container -> {
                container.withNetwork(network);
                container.withNetworkAliases("kafka");
                container.setLabels(Map.of(CONTAINER_LABEL_KEY, "kafka"));

                // Copy OAuth JARs
                TestContainerFactory.copyOAuthJars(container);

                // Copy config files
                TestContainerFactory.copyConfigFiles(container, authServer);

                // Set common OAuth env vars
                TestContainerFactory.configureOAuthEnv(container, authServer, kafkaConfig);

                // Set CLASSPATH for OAuth JARs
                container.addEnv("CLASSPATH", "/opt/kafka/libs/strimzi/*");

                // Set metrics if needed
                if (kafkaConfig.metrics()) {
                    TestContainerFactory.configureMetrics(container);
                }
            });

        // Set custom Kafka image if specified
        if (kafkaImage != null && !kafkaImage.isEmpty()) {
            builder.withImage(kafkaImage);
        }

        kafkaCluster = builder.build();
        try {
            kafkaCluster.start();
        } catch (Exception e) {
            log.error("Kafka cluster failed to start. Dumping container logs:");
            for (StrimziKafkaContainer node : kafkaCluster.getNodes()) {
                dumpContainerLogsOnStartupFailure("kafka-node-" + node.getContainerId(), node);
            }
            dumpAllRunningContainerLogs("kafka-startup-failure");
            throw e;
        }

        // Add all Kafka containers to the allContainers list for log collection
        allContainers.addAll(kafkaCluster.getNodes());

        // Post-start actions
        if (kafkaConfig.setupAcls()) {
            setupACLs();
        }

        if (kafkaConfig.scramUsers().length > 0) {
            setupScramUsers(kafkaConfig.scramUsers());
        }

        if (authServer == AuthServer.MOCK_OAUTH && kafkaConfig.initEndpoints()) {
            initMockOAuthEndpoints();
        }
    }

    /**
     * Get the auth server URL for building OAuth endpoint URIs.
     */
    private String getAuthServerUrl(AuthServer authServer) {
        switch (authServer) {
            case KEYCLOAK:
                return "http://keycloak:8080";
            case MOCK_OAUTH:
                return "https://mockoauth:8090";
            case HYDRA:
                return "https://hydra:4444";
            default:
                return "";
        }
    }

    /**
     * Build the OAUTHBEARER JAAS config string from annotation properties.
     *
     * <p>The default JAAS config uses JWKS endpoint derived from authServerUrl + realm.
     * If oauthProperties contain explicit endpoint URIs, those take precedence.
     */
    static String buildOAuthBearerJaasConfig(KafkaConfig kafkaConfig, String authServerUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required");

        Map<String, String> props = parseProperties(kafkaConfig.oauthProperties());

        appendDefaultJwksAndIssuer(sb, props, authServerUrl, kafkaConfig.realm());

        // Add all explicit oauthProperties (empty values suppress defaults without being written)
        appendNonEmptyProperties(sb, props);

        sb.append(" ;");
        return sb.toString();
    }

    /**
     * Build the OAuth-over-PLAIN JAAS config string from annotation properties.
     */
    static String buildOAuthOverPlainJaasConfig(KafkaConfig kafkaConfig, String authServerUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("org.apache.kafka.common.security.plain.PlainLoginModule required");

        Map<String, String> props = parseProperties(kafkaConfig.oauthProperties());

        // Add default token endpoint if not explicitly overridden
        // An empty value (e.g., "oauth.token.endpoint.uri=") suppresses the default without being written
        String realm = kafkaConfig.realm();
        if (!props.containsKey("oauth.token.endpoint.uri")) {
            sb.append("    oauth.token.endpoint.uri=\"")
                .append(authServerUrl)
                .append("/realms/")
                .append(realm)
                .append("/protocol/openid-connect/token\"");
        }

        appendDefaultJwksAndIssuer(sb, props, authServerUrl, realm);

        // Add all explicit oauthProperties (empty values suppress defaults without being written)
        appendNonEmptyProperties(sb, props);

        sb.append(" ;");
        return sb.toString();
    }

    /**
     * Append default JWKS endpoint and valid issuer URIs to the JAAS config builder,
     * unless they are explicitly overridden by the provided properties.
     */
    private static void appendDefaultJwksAndIssuer(StringBuilder sb, Map<String, String> props, String authServerUrl, String realm) {
        if (!props.containsKey("oauth.jwks.endpoint.uri") && !props.containsKey("oauth.introspection.endpoint.uri")) {
            sb.append("    oauth.jwks.endpoint.uri=\"")
                .append(authServerUrl)
                .append("/realms/")
                .append(realm)
                .append("/protocol/openid-connect/certs\"");
        }
        if (!props.containsKey("oauth.valid.issuer.uri")) {
            sb.append("    oauth.valid.issuer.uri=\"")
                .append(authServerUrl)
                .append("/realms/")
                .append(realm)
                .append("\"");
        }
    }

    /**
     * Append properties with non-empty values to the JAAS config builder.
     * Properties with empty values are skipped — they serve only to suppress auto-generated defaults.
     */
    private static void appendNonEmptyProperties(StringBuilder sb, Map<String, String> props) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (!entry.getValue()
                .isEmpty()) {
                sb.append("    ")
                    .append(entry.getKey())
                    .append("=\"")
                    .append(entry.getValue())
                    .append("\"");
            }
        }
    }

    /**
     * Parse properties from annotation's string array into a map.
     * Each entry is in "key=value" format.
     */
    static Map<String, String> parseProperties(String[] properties) {
        Map<String, String> result = new HashMap<>();
        if (properties != null) {
            for (String prop : properties) {
                int eqIdx = prop.indexOf('=');
                if (eqIdx > 0) {
                    result.put(prop.substring(0, eqIdx)
                        .trim(), prop.substring(eqIdx + 1)
                        .trim());
                }
            }
        }
        return result;
    }

    /**
     * Run the ACL setup commands for authorization tests.
     */
    private void setupACLs() {
        try {
            StrimziKafkaContainer broker = kafkaCluster.getBrokers()
                .iterator()
                .next();
            String internalBootstrap = "kafka:9091";

            // BROKER1 listener is plain PLAINTEXT (no SASL), so connect without auth
            String adminConfig =
                "security.protocol=PLAINTEXT\n" +
                    "default.api.timeout.ms=60000\n";

            execAndCheck(broker, "write admin.properties",
                "cat > /tmp/admin.properties <<'PROPEOF'\n" + adminConfig + "PROPEOF");

            execAndCheck(broker, "kafka-acls bobby topic",
                "/opt/kafka/bin/kafka-acls.sh --bootstrap-server " + internalBootstrap + " --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation Describe --operation Create --operation Write " +
                    "--topic KeycloakAuthorizationTest-multiSaslTest-plain");

            execAndCheck(broker, "kafka-acls bobby cluster",
                "/opt/kafka/bin/kafka-acls.sh --bootstrap-server " + internalBootstrap + " --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation IdempotentWrite --cluster kafka-cluster");

            execAndCheck(broker, "kafka-acls alice topic",
                "/opt/kafka/bin/kafka-acls.sh --bootstrap-server " + internalBootstrap + " --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:alice --operation Describe --operation Create --operation Write " +
                    "--topic KeycloakAuthorizationTest-multiSaslTest-scram");

            execAndCheck(broker, "kafka-acls alice cluster",
                "/opt/kafka/bin/kafka-acls.sh --bootstrap-server " + internalBootstrap + " --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:alice --operation IdempotentWrite --cluster kafka-cluster");

            log.info("ACL setup completed successfully");
        } catch (Exception e) {
            log.error("Failed to set up ACLs", e);
            throw new RuntimeException("ACL setup failed", e);
        }
    }

    /**
     * If an authorizer is configured, ensure User:ANONYMOUS is in super.users.
     * BROKER1 listener is PLAINTEXT (no auth), so inter-broker connections
     * authenticate as ANONYMOUS and need super.users to allow CLUSTER_ACTION.
     */
    private static void ensureAnonymousSuperUser(Map<String, String> config) {
        if (config.containsKey("authorizer.class.name")) {
            String superUsers = config.getOrDefault("super.users", "");
            if (!superUsers.contains("User:ANONYMOUS")) {
                superUsers = superUsers.isEmpty() ? "User:ANONYMOUS" : superUsers + ";User:ANONYMOUS";
                config.put("super.users", superUsers);
            }
        }
    }

    /**
     * Provision SCRAM-SHA-512 users on the Kafka broker.
     * Each entry is in "username:password" format.
     */
    private void setupScramUsers(String[] scramUsers) {
        try {
            StrimziKafkaContainer broker = kafkaCluster.getBrokers()
                .iterator()
                .next();
            String internalBootstrap = "kafka:9091";

            for (String entry : scramUsers) {
                int colonIdx = entry.indexOf(':');
                if (colonIdx <= 0) {
                    throw new IllegalArgumentException("Invalid scramUsers entry (expected 'username:password'): " + entry);
                }
                String username = entry.substring(0, colonIdx);
                String password = entry.substring(colonIdx + 1);

                execAndCheck(broker, "scram-user " + username,
                    "/opt/kafka/bin/kafka-configs.sh --bootstrap-server " + internalBootstrap +
                        " --alter --add-config 'SCRAM-SHA-512=[password=" + password + "]'" +
                        " --entity-type users --entity-name " + username);
            }
            log.info("SCRAM user provisioning completed successfully");
        } catch (Exception e) {
            log.error("Failed to provision SCRAM users", e);
            throw new RuntimeException("SCRAM user provisioning failed", e);
        }
    }

    private void execAndCheck(StrimziKafkaContainer container, String description, String command) throws Exception {
        Container.ExecResult result = container.execInContainer("bash", "-c", command);
        if (result.getExitCode() != 0) {
            log.error("{} failed (exit code {}): stdout={}, stderr={}", description, result.getExitCode(), result.getStdout(), result.getStderr());
            throw new RuntimeException(description + " failed with exit code " + result.getExitCode() + ": " + result.getStderr());
        }
        log.info("{} succeeded: {}", description, result.getStdout()
            .trim());
    }

    /**
     * Dump a container's logs to console and to a startup failure log file.
     * Wrapped in try-catch so logging failures don't mask the original error.
     */
    private void dumpContainerLogsOnStartupFailure(String name, GenericContainer<?> container) {
        try {
            String logs = container.getLogs();
            log.error("=== {} startup failure logs ===\n{}", name, logs);

            File dir = containersDir(testLogDirPath);
            dir.mkdirs();
            File logFile = new File(dir, name + "-startup-failure.log");
            try (FileWriter writer = new FileWriter(logFile)) {
                writer.write(logs);
            }
        } catch (Exception ex) {
            log.error("Failed to dump startup failure logs for {}: {}", name, ex.getMessage());
        }
    }

    /**
     * Dump logs from all currently tracked containers (for cross-container context on startup failures).
     */
    private void dumpAllRunningContainerLogs(String suffix) {
        for (GenericContainer<?> container : allContainers) {
            try {
                String label = container.getLabels()
                    .getOrDefault(CONTAINER_LABEL_KEY, "unknown");
                String logs = container.getLogs();
                log.error("=== {} logs (context for {}) ===\n{}", label, suffix, logs);

                File dir = containersDir(testLogDirPath);
                dir.mkdirs();
                File logFile = new File(dir, label + "-" + suffix + ".log");
                try (FileWriter writer = new FileWriter(logFile)) {
                    writer.write(logs);
                }
            } catch (Exception ex) {
                log.error("Failed to dump container logs: {}", ex.getMessage());
            }
        }
    }

    /**
     * Collect logs from all non-Kafka containers at teardown time.
     * Kafka logs are handled by {@code StrimziKafkaCluster.withLogCollection()}.
     */
    private void collectAllContainerLogs() {
        File dir = containersDir(testLogDirPath);
        dir.mkdirs();

        for (GenericContainer<?> container : allContainers) {
            if (container instanceof StrimziKafkaContainer) {
                // Kafka container logs are collected by withLogCollection() on stop()
                continue;
            }
            try {
                String label = container.getLabels()
                    .getOrDefault(CONTAINER_LABEL_KEY, "unknown");
                File logFile = new File(dir, label + ".log");
                try (FileWriter writer = new FileWriter(logFile)) {
                    writer.write(container.getLogs());
                }
            } catch (Exception e) {
                log.error("Failed to collect container logs: {}", e.getMessage());
            }
        }
    }

    /**
     * Copy {@code target/test.log} to {@code target/test-logs/{className}/test.log}.
     * Since {@code reuseForks=false}, each JVM fork runs one test class, so
     * {@code target/test.log} at afterAll time contains exactly this class's logs.
     */
    private void copyTestLog() {
        try {
            Path source = Path.of("target/test.log");
            if (Files.exists(source)) {
                File classLogDir = TestLogPaths.classDir(testLogDirPath);
                classLogDir.mkdirs();
                Files.copy(source, classLogDir.toPath()
                    .resolve("test.log"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to copy test.log: {}", e.getMessage());
        }
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
