/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.oauth.testsuite.metrics.TestMetricsReporter;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Factory methods for creating common test containers (Keycloak, MockOAuth, Hydra)
 * and helper methods for configuring Kafka containers with OAuth.
 */
public class TestContainerFactory {
    public static final String CONTAINER_LABEL_KEY = "container";

    // Used container images
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.3.3";
    private static final String MOCKOAUTH_IMAGE = "testsuite/mock-oauth-server:latest";
    private static final String HYDRA_IMAGE = "oryd/hydra:v2.3.0";
    private static final String HYDRA_IMPORT_IMAGE = "testsuite/hydra-import:latest";

    // Timeouts - Keycloak requires longer timeout due to realms import
    private static final int CONTAINER_STARTUP_TIMEOUT_LONG_IN_SECONDS = 90;
    private static final int CONTAINER_STARTUP_TIMEOUT_IN_SECONDS = 30;

    /**
     * Create a standard Keycloak container with realm imports, keystore, admin creds, and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured Keycloak container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createKeycloak(Network network) {
        return new GenericContainer<>(KEYCLOAK_IMAGE)
            .withLabel(CONTAINER_LABEL_KEY, "keycloak")
            .withExposedPorts(8080)
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withFileSystemBind(
                Path.of("docker/keycloak/realms")
                    .toAbsolutePath()
                    .toString(),
                "/opt/keycloak/data/import", BindMode.READ_ONLY)
            .withFileSystemBind(
                Path.of("docker/certificates/keycloak.server.keystore.p12")
                    .toAbsolutePath()
                    .toString(),
                "/opt/keycloak/data/certs/keycloak.server.keystore.p12", BindMode.READ_ONLY)
            .withCommand("-v start --import-realm --features=token-exchange,authorization,scripts")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HOSTNAME", "http://keycloak:8080")
            .withEnv("KC_HTTP_ENABLED", "true")
            .waitingFor(Wait.forLogMessage(".*Listening on:.*", 1)
                .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_LONG_IN_SECONDS)));
    }

    /**
     * Create a MockOAuth server container with 3 keystores and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured MockOAuth container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createMockOAuth(Network network) {
        return new GenericContainer<>(MOCKOAUTH_IMAGE)
            .withLabel(CONTAINER_LABEL_KEY, "mockoauth")
            .withNetwork(network)
            .withNetworkAliases("mockoauth")
            .withExposedPorts(8090, 8091)
            .withCopyToContainer(
                MountableFile.forHostPath(Path.of("docker/certificates")
                    .toAbsolutePath()
                    .toString()),
                "/application/config")
            .withEnv("KEYSTORE_ONE_PATH", "/application/config/mockoauth.server.keystore.p12")
            .withEnv("KEYSTORE_ONE_PASSWORD", "changeit")
            .withEnv("KEYSTORE_TWO_PATH", "/application/config/mockoauth.server.keystore_2.p12")
            .withEnv("KEYSTORE_TWO_PASSWORD", "changeit")
            .withEnv("KEYSTORE_EXPIRED_PATH", "/application/config/mockoauth.server.keystore_expired.p12")
            .withEnv("KEYSTORE_EXPIRED_PASSWORD", "changeit")
            .waitingFor(Wait.forLogMessage(".*Succeeded in deploying verticle.*", 1)
                .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_IN_SECONDS)));
    }

    /**
     * Create a Hydra container (opaque token strategy) with TLS certs and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured Hydra container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createHydra(Network network) {
        return new GenericContainer<>(HYDRA_IMAGE)
            .withLabel(CONTAINER_LABEL_KEY, "hydra")
            .withExposedPorts(4444, 4445)
            .withNetwork(network)
            .withNetworkAliases("hydra")
            .withCopyToContainer(
                MountableFile.forHostPath(Path.of("target/hydra/certs")
                    .toAbsolutePath()
                    .toString()),
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
                .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_IN_SECONDS)));
    }

    /**
     * Create a Hydra import init container that provisions OAuth clients into Hydra.
     *
     * @param network   The Docker network to attach to
     * @param hydraUri  The Hydra admin clients URI (e.g., "<a href="https://hydra:4445/admin/clients">...</a>")
     * @param adminPort The Hydra admin port
     * @return A configured Hydra import container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createHydraImport(Network network, String hydraUri, String adminPort) {
        return new GenericContainer<>(HYDRA_IMPORT_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("hydra-import")
            .withCopyToContainer(
                MountableFile.forHostPath(Path.of("docker/hydra-import/scripts")
                    .toAbsolutePath()
                    .toString()),
                "/hydra")
            .withCopyToContainer(
                MountableFile.forHostPath(Path.of("target/hydra-import/certs")
                    .toAbsolutePath()
                    .toString()),
                "/hydra/certs")
            .withEnv("HYDRA_URI", hydraUri)
            .withEnv("SERVE_ADMIN_PORT", adminPort)
            .withCommand("/bin/bash", "-c", "cd hydra && ./start.sh")
            .waitingFor(Wait.forLogMessage(".*Hydra import complete.*", 1)
                .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_IN_SECONDS)));
    }

    /**
     * Create a Hydra JWT container (JWT token strategy) with TLS certs and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured Hydra JWT container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createHydraJwt(Network network) {
        return new GenericContainer<>(HYDRA_IMAGE)
            .withLabel(CONTAINER_LABEL_KEY, "hydra-jwt")
            .withExposedPorts(4454, 4455)
            .withNetwork(network)
            .withNetworkAliases("hydra-jwt")
            .withCopyToContainer(
                MountableFile.forHostPath(Path.of("target/hydra/certs")
                    .toAbsolutePath()
                    .toString()),
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
                .withStartupTimeout(Duration.ofSeconds(CONTAINER_STARTUP_TIMEOUT_IN_SECONDS)));
    }

    /**
     * Copy OAuth SNAPSHOT JARs from the build output into the Kafka container.
     * These JARs are placed in {@code /opt/kafka/libs/strimzi/} and take precedence
     * over any bundled OAuth JARs via the CLASSPATH env var.
     *
     * @param container The Kafka container to copy JARs into
     */
    public static void copyOAuthJars(StrimziKafkaContainer container) {
        Path libsDir = Path.of("target/kafka/libs")
            .toAbsolutePath();
        File dir = libsDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    container.withCopyToContainer(
                        MountableFile.forHostPath(jar.getAbsolutePath()),
                        "/opt/kafka/libs/strimzi/" + jar.getName());
                }
            }
        }

        // Also copy the testsuite shaded jar (contains TestMetricsReporter and other test helpers)
        File targetDir = Path.of("target")
            .toAbsolutePath()
            .toFile();
        if (targetDir.exists() && targetDir.isDirectory()) {
            File[] testsuiteJars = targetDir.listFiles((d, name) ->
                name.startsWith("testsuite-") && name.endsWith(".jar")
                    && !name.contains("-fat") && !name.contains("-sources"));
            if (testsuiteJars != null && testsuiteJars.length > 0) {
                container.withCopyToContainer(
                    MountableFile.forHostPath(testsuiteJars[0].getAbsolutePath()),
                    "/opt/kafka/libs/strimzi/testsuite.jar");
            }
        }
    }

    /**
     * Copy configuration files (log4j, metrics config) into the Kafka container.
     * For auth servers that use TLS (MockOAuth, Hydra), also copies the CA truststore.
     *
     * @param container  The Kafka container to copy files into
     * @param authServer The auth server type (determines whether certs are needed)
     */
    public static void copyConfigFiles(StrimziKafkaContainer container, AuthServer authServer) {
        // Copy log4j and metrics config
        copyDirToContainer(container, Path.of("docker/kafka/config")
            .toAbsolutePath(), "/opt/kafka/config/strimzi");

        // Set custom log4j config
        container.addEnv("KAFKA_LOG4J_OPTS", "-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties");

        // For TLS-based auth servers, copy the CA truststore
        if (authServer == AuthServer.MOCK_OAUTH || authServer == AuthServer.HYDRA) {
            copyDirToContainer(container, Path.of("target/kafka/certs")
                .toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
        }
    }

    /**
     * Set common OAuth environment variables on the Kafka container based on the auth server type.
     *
     * @param container   The Kafka container to configure
     * @param authServer  The auth server type
     * @param kafkaConfig The Kafka configuration annotation
     */
    public static void configureOAuthEnv(StrimziKafkaContainer container, AuthServer authServer, KafkaConfig kafkaConfig) {
        switch (authServer) {
            case MOCK_OAUTH:
                container.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
                container.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
                container.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");
                container.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "10");
                container.addEnv("OAUTH_READ_TIMEOUT_SECONDS", "10");
                break;

            case KEYCLOAK:
                container.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
                container.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");
                break;

            case HYDRA:
                container.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
                container.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
                container.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");
                container.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");
                container.addEnv("OAUTH_READ_TIMEOUT_SECONDS", "20");
                container.addEnv("HYDRA_HOST", "hydra");
                break;

            default:
                break;
        }
    }

    /**
     * Configure Prometheus JMX metrics on the Kafka container.
     * Overrides {@code kafka-server-start.sh} with a wrapper that sets the JMX Prometheus
     * javaagent only for the broker process. We cannot use {@code KAFKA_OPTS} as a container-wide
     * env var because it would also be picked up by CLI tools like {@code kafka-metadata-quorum.sh}
     * (used by StrimziKafkaCluster's quorum readiness check), causing a port binding conflict.
     *
     * @param container The Kafka container to configure
     */
    public static void configureMetrics(StrimziKafkaContainer container) {
        // Enable OAuth metrics
        container.addEnv("OAUTH_ENABLE_METRICS", "true");
        container.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS",
            TestMetricsReporter.class.getName() + ",org.apache.kafka.common.metrics.JmxReporter");

        // Expose the Prometheus metrics port so tests can access it via mapped port
        container.addExposedPort(9404);

        // Override kafka-server-start.sh with a wrapper that applies the javaagent
        // only for the broker JVM, similar to how Strimzi production images handle it
        // via KAFKA_JMX_EXPORTER_ENABLED in their starter script.
        String prometheusVersion = findPrometheusAgentVersion();
        if (prometheusVersion != null) {
            String javaagentOpt = "-javaagent:/opt/kafka/libs/strimzi/jmx_prometheus_javaagent-" + prometheusVersion +
                ".jar=9404:/opt/kafka/config/strimzi/metrics-config.yml";
            String wrapperScript =
                "#!/bin/bash\n" +
                    "export KAFKA_OPTS=\"${KAFKA_OPTS:-} " + javaagentOpt + "\"\n" +
                    "exec $(dirname $0)/kafka-run-class.sh $EXTRA_ARGS kafka.Kafka \"$@\"\n";
            container.withCopyToContainer(
                Transferable.of(wrapperScript.getBytes(StandardCharsets.UTF_8), 0755),
                "/opt/kafka/bin/kafka-server-start.sh");
        }
    }

    /**
     * Find the Prometheus JMX agent version from the build output directory.
     *
     * @return The version string (e.g., "0.20.0"), or null if not found
     */
    static String findPrometheusAgentVersion() {
        Path libsDir = Path.of("target/kafka/libs")
            .toAbsolutePath();
        File dir = libsDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.startsWith("jmx_prometheus_javaagent") && name.endsWith(".jar"));
            if (files != null && files.length > 0) {
                String name = files[0].getName();
                // Extract version: jmx_prometheus_javaagent-X.Y.Z.jar
                int start = name.indexOf('-') + 1;
                int end = name.lastIndexOf('.');
                if (start > 0 && end > start) {
                    return name.substring(start, end);
                }
            }
        }
        return null;
    }

    /**
     * Copy all files from a host directory into the container at the specified path.
     *
     * @param container     The container to copy files into
     * @param hostDir       The local directory whose files to copy
     * @param containerPath The destination directory inside the container
     */
    private static void copyDirToContainer(StrimziKafkaContainer container, Path hostDir, String containerPath) {
        File dir = hostDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        container.withCopyToContainer(
                            MountableFile.forHostPath(file.getAbsolutePath()),
                            containerPath + "/" + file.getName());
                    }
                }
            }
        }
    }
}
