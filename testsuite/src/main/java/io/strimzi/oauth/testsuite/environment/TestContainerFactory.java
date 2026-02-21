/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

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
 * Factory methods for creating common test containers (Keycloak, MockOAuth)
 * and helper methods for configuring Kafka containers with OAuth.
 */
public class TestContainerFactory {
    public static final String CONTAINER_LABEL_KEY = "container";

    /**
     * Create a standard Keycloak 26.3.3 container with realm imports, keystore, admin creds, and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured Keycloak container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createKeycloak(Network network) {
        return new GenericContainer<>("quay.io/keycloak/keycloak:26.3.3")
                .withLabel(CONTAINER_LABEL_KEY, "keycloak")
                .withExposedPorts(8080)
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withFileSystemBind(
                        Path.of("docker/keycloak/realms").toAbsolutePath().toString(),
                        "/opt/keycloak/data/import", BindMode.READ_ONLY)
                .withFileSystemBind(
                        Path.of("docker/certificates/keycloak.server.keystore.p12").toAbsolutePath().toString(),
                        "/opt/keycloak/data/certs/keycloak.server.keystore.p12", BindMode.READ_ONLY)
                .withCommand("-v start --import-realm --features=token-exchange,authorization,scripts")
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HOSTNAME", "http://keycloak:8080")
                .withEnv("KC_HTTP_ENABLED", "true")
                .waitingFor(Wait.forLogMessage(".*Listening on:.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }

    /**
     * Create a MockOAuth server container with 3 keystores and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured MockOAuth container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createMockOAuth(Network network) {
        return new GenericContainer<>("testsuite/mock-oauth-server")
                .withLabel(CONTAINER_LABEL_KEY, "mockoauth")
                .withNetwork(network)
                .withNetworkAliases("mockoauth")
                .withExposedPorts(8090, 8091)
                .withCopyToContainer(
                        MountableFile.forHostPath(Path.of("docker/certificates").toAbsolutePath().toString()),
                        "/application/config")
                .withEnv("KEYSTORE_ONE_PATH", "/application/config/mockoauth.server.keystore.p12")
                .withEnv("KEYSTORE_ONE_PASSWORD", "changeit")
                .withEnv("KEYSTORE_TWO_PATH", "/application/config/mockoauth.server.keystore_2.p12")
                .withEnv("KEYSTORE_TWO_PASSWORD", "changeit")
                .withEnv("KEYSTORE_EXPIRED_PATH", "/application/config/mockoauth.server.keystore_expired.p12")
                .withEnv("KEYSTORE_EXPIRED_PASSWORD", "changeit")
                .waitingFor(Wait.forLogMessage(".*Succeeded in deploying verticle.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
    }

    /**
     * Publish MockOAuth mapped ports as system properties so test code can reach the mock OAuth server.
     *
     * @param mockoauth The started MockOAuth container
     */
    public static void publishMockOAuthPorts(GenericContainer<?> mockoauth) {
        System.setProperty("mockoauth.host", mockoauth.getHost());
        System.setProperty("mockoauth.port", String.valueOf(mockoauth.getMappedPort(8090)));
        System.setProperty("mockoauth.admin.port", String.valueOf(mockoauth.getMappedPort(8091)));
    }

    /**
     * Publish Keycloak mapped port as system properties so test code can reach the Keycloak server.
     *
     * @param keycloak The started Keycloak container
     */
    public static void publishKeycloakPort(GenericContainer<?> keycloak) {
        System.setProperty("keycloak.host", keycloak.getHost());
        System.setProperty("keycloak.port", String.valueOf(keycloak.getMappedPort(8080)));
    }

    /**
     * Copy OAuth SNAPSHOT JARs from the build output into the Kafka container.
     * These JARs are placed in {@code /opt/kafka/libs/strimzi/} and take precedence
     * over any bundled OAuth JARs via the CLASSPATH env var.
     *
     * @param container The Kafka container to copy JARs into
     */
    public static void copyOAuthJars(StrimziKafkaContainer container) {
        Path libsDir = Path.of("target/kafka/libs").toAbsolutePath();
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
        Path testsuiteJar = Path.of("target/testsuite-1.0.0-SNAPSHOT.jar").toAbsolutePath();
        if (testsuiteJar.toFile().exists()) {
            container.withCopyToContainer(
                    MountableFile.forHostPath(testsuiteJar.toString()),
                    "/opt/kafka/libs/strimzi/testsuite.jar");
        }
    }

    /**
     * Copy configuration files (log4j, metrics config) into the Kafka container.
     * For auth servers that use TLS (MockOAuth, Hydra), also copies the CA truststore.
     *
     * @param container The Kafka container to copy files into
     * @param authServer The auth server type (determines whether certs are needed)
     */
    public static void copyConfigFiles(StrimziKafkaContainer container, AuthServer authServer) {
        // Copy log4j and metrics config
        copyDirToContainer(container, Path.of("docker/kafka/config").toAbsolutePath(), "/opt/kafka/config/strimzi");

        // Set custom log4j config
        container.addEnv("KAFKA_LOG4J_OPTS", "-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties");

        // For TLS-based auth servers, copy the CA truststore
        if (authServer == AuthServer.MOCK_OAUTH || authServer == AuthServer.HYDRA) {
            copyDirToContainer(container, Path.of("target/kafka/certs").toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
        }
    }

    /**
     * Set common OAuth environment variables on the Kafka container based on the auth server type.
     *
     * @param container The Kafka container to configure
     * @param authServer The auth server type
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
     * @param authServer The auth server type (determines reporter configuration)
     */
    public static void configureMetrics(StrimziKafkaContainer container, AuthServer authServer) {
        // Enable OAuth metrics
        container.addEnv("OAUTH_ENABLE_METRICS", "true");
        container.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS",
                "io.strimzi.oauth.testsuite.metrics.TestMetricsReporter,org.apache.kafka.common.metrics.JmxReporter");

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
        Path libsDir = Path.of("target/kafka/libs").toAbsolutePath();
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
     * @param container The container to copy files into
     * @param hostDir The local directory whose files to copy
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
