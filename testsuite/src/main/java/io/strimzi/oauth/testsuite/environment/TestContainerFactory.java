/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.test.container.OAuthKafkaContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Factory methods for creating common test containers (Keycloak, MockOAuth, Kafka).
 */
public class TestContainerFactory {

    /**
     * Create a standard Keycloak 26.3.3 container with realm imports, keystore, admin creds, and wait strategy.
     *
     * @param network The Docker network to attach to
     * @return A configured Keycloak container (not yet started)
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> createKeycloak(Network network) {
        return new GenericContainer<>("quay.io/keycloak/keycloak:26.3.3")
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
                        .withStartupTimeout(Duration.ofSeconds(60)));
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
                        .withStartupTimeout(Duration.ofSeconds(180)));
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
     * Create a base OAuthKafkaContainer with nodeId, network, OAuth JARs, config dir,
     * and SCRAM users (admin + alice).
     *
     * @param network The Docker network to attach to
     * @return A configured OAuthKafkaContainer (not yet started, needs kafkaConfigurationMap and ports)
     */
    public static OAuthKafkaContainer createKafkaBase(Network network) {
        String kafkaImage = System.getProperty("KAFKA_DOCKER_IMAGE");
        OAuthKafkaContainer kafka = new OAuthKafkaContainer(kafkaImage);
        kafka.withNodeId(1);
        kafka.withNetwork(network);
        kafka.withNetworkAliases("kafka");
        kafka.withOAuthJars(Path.of("target/kafka/libs").toAbsolutePath());
        kafka.withCopyDirToContainer(Path.of("docker/kafka/config").toAbsolutePath(), "/opt/kafka/config/strimzi");
        kafka.withScramUser("admin", "admin-secret");
        kafka.withScramUser("alice", "alice-secret");
        return kafka;
    }

    /**
     * Add standard KRaft base configuration to a Kafka config map:
     * process.roles, controller quorum, CONTROLLER listener, principal builder, offsets replication factor.
     *
     * @param configMap The config map to add base properties to
     */
    public static void addKRaftBaseConfig(Map<String, String> configMap) {
        configMap.put("process.roles", "broker,controller");
        configMap.put("controller.quorum.voters", "1@kafka:9091");
        configMap.put("controller.listener.names", "CONTROLLER");
        configMap.put("sasl.mechanism.controller.protocol", "SCRAM-SHA-512");

        configMap.put("principal.builder.class", "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");
        configMap.put("offsets.topic.replication.factor", "1");

        // CONTROLLER listener
        configMap.put("listener.name.controller.sasl.enabled.mechanisms", "SCRAM-SHA-512");
        configMap.put("listener.name.controller.scram-sha-512.sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required    username=\"admin\"    password=\"admin-secret\" ;");
    }
}
