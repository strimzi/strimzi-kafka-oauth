/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.hydra;

import io.strimzi.test.container.OAuthKafkaContainer;
import io.strimzi.testsuite.oauth.support.TestContainerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Docker containers for Hydra authentication tests using programmatic Testcontainers.
 *
 * <p>Start order: hydra -> hydra-import -> hydra-jwt -> hydra-jwt-import -> kafka</p>
 */
public class HydraTestEnvironment {

    private Network network;
    private FixedHostPortGenericContainer<?> hydra;
    private GenericContainer<?> hydraImport;
    private FixedHostPortGenericContainer<?> hydraJwt;
    private GenericContainer<?> hydraJwtImport;
    private OAuthKafkaContainer kafka;

    /**
     * Start all the containers in the required order.
     */
    public void start() {
        network = Network.newNetwork();

        // Start Hydra (opaque token strategy)
        hydra = new FixedHostPortGenericContainer<>("oryd/hydra:v2.3.0")
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
                        .withStartupTimeout(Duration.ofSeconds(30)))
                .withFixedExposedPort(4444, 4444)
                .withFixedExposedPort(4445, 4445);
        hydra.addExposedPort(4444);
        hydra.addExposedPort(4445);
        hydra.start();

        // Run hydra-import init container (imports OAuth clients into Hydra)
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

        // Start Hydra JWT (JWT token strategy)
        hydraJwt = new FixedHostPortGenericContainer<>("oryd/hydra:v2.3.0")
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
                        .withStartupTimeout(Duration.ofSeconds(30)))
                .withFixedExposedPort(4454, 4454)
                .withFixedExposedPort(4455, 4455);
        hydraJwt.addExposedPort(4454);
        hydraJwt.addExposedPort(4455);
        hydraJwt.start();

        // Run hydra-jwt-import init container (imports OAuth clients into Hydra JWT)
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

        // Build Kafka configuration map
        Map<String, String> kafkaConfigMap = buildKafkaConfigMap();

        // Start Kafka
        kafka = TestContainerFactory.createKafkaBase(network);
        kafka.withCopyDirToContainer(Path.of("target/kafka/certs").toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
        kafka.withKafkaConfigurationMap(kafkaConfigMap);

        // OAuth env vars - set via addEnv(), NOT kafkaConfigurationMap
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");

        // For start_with_hydra.sh script to know where hydra is listening
        kafka.addEnv("HYDRA_HOST", "hydra");

        // Fixed exposed ports for Kafka listeners
        kafka.addFixedExposedPort(9091, 9091);
        kafka.addFixedExposedPort(9092, 9092);
        kafka.addFixedExposedPort(9093, 9093);

        kafka.waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));
        kafka.start();
    }

    /**
     * Stop all the containers and clean up the network.
     */
    public void stop() {
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
        if (network != null) {
            network.close();
        }
    }

    /**
     * Get all managed containers for log collection.
     *
     * @return A list of all containers
     */
    public List<GenericContainer<?>> getContainers() {
        List<GenericContainer<?>> containers = new ArrayList<>();
        if (hydra != null) {
            containers.add(hydra);
        }
        if (hydraImport != null) {
            containers.add(hydraImport);
        }
        if (hydraJwt != null) {
            containers.add(hydraJwt);
        }
        if (hydraJwtImport != null) {
            containers.add(hydraJwtImport);
        }
        if (kafka != null) {
            containers.add(kafka);
        }
        return containers;
    }

    /**
     * Get the Kafka container.
     *
     * @return The OAuthKafkaContainer
     */
    public OAuthKafkaContainer getKafka() {
        return kafka;
    }

    private static Map<String, String> buildKafkaConfigMap() {
        Map<String, String> configMap = new HashMap<>();

        // KRaft base properties
        TestContainerFactory.addKRaftBaseConfig(configMap);

        // Listeners
        configMap.put("listeners", "CONTROLLER://kafka:9091,INTROSPECT://kafka:9092,JWT://kafka:9093");
        configMap.put("advertised.listeners", "INTROSPECT://localhost:9092,JWT://localhost:9093");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT");
        configMap.put("sasl.enabled.mechanisms", "OAUTHBEARER");
        configMap.put("inter.broker.listener.name", "INTROSPECT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // INTROSPECT listener
        configMap.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + "    oauth.introspection.endpoint.uri=\"https://hydra:4445/admin/oauth2/introspect\""
                + "    oauth.client.id=\"kafka-broker\""
                + "    oauth.client.secret=\"kafka-broker-secret\""
                + "    oauth.valid.issuer.uri=\"https://hydra:4444/\""
                + "    oauth.token.endpoint.uri=\"https://hydra:4444/oauth2/token\""
                + "    oauth.check.access.token.type=\"false\""
                + "    oauth.access.token.is.jwt=\"false\" ;");
        configMap.put("listener.name.introspect.oauthbearer.sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
        configMap.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWT listener
        configMap.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
                + "    oauth.jwks.endpoint.uri=\"https://hydra-jwt:4454/.well-known/jwks.json\""
                + "    oauth.valid.issuer.uri=\"https://hydra-jwt:4454/\""
                + "    oauth.check.access.token.type=\"false\""
                + "    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        return configMap;
    }
}
