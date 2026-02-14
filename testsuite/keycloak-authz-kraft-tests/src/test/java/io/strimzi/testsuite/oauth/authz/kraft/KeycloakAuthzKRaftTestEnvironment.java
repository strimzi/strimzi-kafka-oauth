/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.authz.kraft;

import io.strimzi.test.container.OAuthKafkaContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testcontainers.containers.Container;

/**
 * Manages the Docker containers for Keycloak Authorization KRaft tests using programmatic Testcontainers.
 */
public class KeycloakAuthzKRaftTestEnvironment {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthzKRaftTestEnvironment.class);

    private Network network;
    private FixedHostPortGenericContainer<?> keycloak;
    private OAuthKafkaContainer kafka;

    /**
     * Start all the containers.
     */
    public void start() {
        network = Network.newNetwork();

        // Start Keycloak first
        keycloak = new FixedHostPortGenericContainer<>("quay.io/keycloak/keycloak:26.3.3")
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withFileSystemBind(
                        Path.of("../docker/keycloak/realms").toAbsolutePath().toString(),
                        "/opt/keycloak/data/import", BindMode.READ_ONLY)
                .withFileSystemBind(
                        Path.of("../docker/certificates/keycloak.server.keystore.p12").toAbsolutePath().toString(),
                        "/opt/keycloak/data/certs/keycloak.server.keystore.p12", BindMode.READ_ONLY)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HOSTNAME", "keycloak")
                .withEnv("KC_HTTP_ENABLED", "true")
                .withCommand("-v start --import-realm --features=token-exchange,authorization,scripts")
                .waitingFor(Wait.forLogMessage(".*Listening on:.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(60)));
        keycloak.addExposedPort(8080);
        keycloak.withFixedExposedPort(8080, 8080);
        keycloak.start();

        // Build Kafka configuration map
        Map<String, String> kafkaConfigMap = buildKafkaConfigMap();

        String kafkaImage = System.getProperty("KAFKA_DOCKER_IMAGE");
        kafka = new OAuthKafkaContainer(kafkaImage);
        kafka.withNodeId(1);
        kafka.withNetwork(network);
        kafka.withNetworkAliases("kafka");
        kafka.withOAuthJars(Path.of("../docker/target/kafka/libs").toAbsolutePath());
        kafka.withCopyDirToContainer(Path.of("../docker/kafka/config").toAbsolutePath(), "/opt/kafka/config/strimzi");
        kafka.withScramUser("admin", "admin-secret");
        kafka.withScramUser("alice", "alice-secret");
        kafka.withMetrics("/opt/kafka/config/strimzi/metrics-config.yml");
        kafka.withKafkaConfigurationMap(kafkaConfigMap);

        // OAuth env vars - set via addEnv(), NOT kafkaConfigurationMap
        kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
        kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");
        kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
        kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter");

        // For start.sh script to know where the keycloak is listening
        kafka.addEnv("KEYCLOAK_HOST", "keycloak");

        // Fixed exposed ports for Kafka listeners
        kafka.addFixedExposedPort(9091, 9091);
        kafka.addFixedExposedPort(9092, 9092);
        kafka.addFixedExposedPort(9093, 9093);
        kafka.addFixedExposedPort(9094, 9094);
        kafka.addFixedExposedPort(9095, 9095);
        kafka.addFixedExposedPort(9096, 9096);
        kafka.addFixedExposedPort(9100, 9100);
        kafka.addFixedExposedPort(9101, 9101);

        // Prometheus JMX Exporter
        kafka.addFixedExposedPort(9404, 9404);

        kafka.waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));
        kafka.start();

        // Run ACL setup after Kafka starts
        setupACLs();
    }

    /**
     * Run the ACL setup commands that were previously in the kafka-acls init container.
     * These commands add ACLs for users 'bobby' and 'alice' using the PLAIN listener on port 9100.
     */
    private void setupACLs() {
        try {
            String adminConfig =
                    "security.protocol=SASL_PLAINTEXT\n" +
                    "sasl.mechanism=PLAIN\n" +
                    "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"admin-password\" ;\n" +
                    "default.api.timeout.ms=210000\n";

            // Write admin.properties into the container using a heredoc for reliability
            execAndCheck("write admin.properties",
                    "cat > /tmp/admin.properties <<'PROPEOF'\n" + adminConfig + "PROPEOF");

            // Add ACL for user 'bobby'
            execAndCheck("kafka-acls bobby topic",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation Describe --operation Create --operation Write " +
                    "--topic KeycloakAuthorizationTest-multiSaslTest-plain");

            execAndCheck("kafka-acls bobby cluster",
                    "/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config /tmp/admin.properties " +
                    "--add --allow-principal User:bobby --operation IdempotentWrite --cluster kafka-cluster");

            // Add ACL for user 'alice'
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
     * Stop all the containers and clean up the network.
     */
    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
        if (keycloak != null) {
            keycloak.stop();
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
        if (keycloak != null) {
            containers.add(keycloak);
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

        // KRaft properties
        configMap.put("process.roles", "broker,controller");
        configMap.put("controller.quorum.voters", "1@kafka:9091");
        configMap.put("controller.listener.names", "CONTROLLER");
        configMap.put("sasl.mechanism.controller.protocol", "SCRAM-SHA-512");

        // Listeners
        configMap.put("listeners", "CONTROLLER://0.0.0.0:9091,JWT://0.0.0.0:9092,INTROSPECT://0.0.0.0:9093,JWTPLAIN://0.0.0.0:9094,INTROSPECTPLAIN://0.0.0.0:9095,JWTREFRESH://0.0.0.0:9096,PLAIN://0.0.0.0:9100,SCRAM://0.0.0.0:9101");
        configMap.put("advertised.listeners", "JWT://localhost:9092,INTROSPECT://localhost:9093,JWTPLAIN://localhost:9094,INTROSPECTPLAIN://localhost:9095,JWTREFRESH://localhost:9096,PLAIN://localhost:9100,SCRAM://localhost:9101");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,INTROSPECTPLAIN:SASL_PLAINTEXT,JWTREFRESH:SASL_PLAINTEXT,PLAIN:SASL_PLAINTEXT,SCRAM:SASL_PLAINTEXT");

        // Inter-broker
        configMap.put("inter.broker.listener.name", "JWT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // Common settings
        configMap.put("principal.builder.class", "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");
        configMap.put("offsets.topic.replication.factor", "1");

        // CONTROLLER listener
        configMap.put("listener.name.controller.sasl.enabled.mechanisms", "SCRAM-SHA-512");
        configMap.put("listener.name.controller.scram-sha-512.sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required    username=\"admin\"    password=\"admin-secret\" ;");

        // JWT listener
        configMap.put("listener.name.jwt.sasl.enabled.mechanisms", "OAUTHBEARER");
        configMap.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required" +
                "    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"" +
                "    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"" +
                "    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"" +
                "    oauth.client.id=\"kafka\"" +
                "    oauth.client.secret=\"kafka-secret\"" +
                "    oauth.groups.claim=\"$.realm_access.roles\"" +
                "    oauth.fallback.username.claim=\"username\" ;");
        configMap.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.jwt.oauthbearer.sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        // INTROSPECT listener
        configMap.put("listener.name.introspect.sasl.enabled.mechanisms", "OAUTHBEARER");
        configMap.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required" +
                "    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"" +
                "    oauth.client.id=\"kafka\"" +
                "    oauth.client.secret=\"kafka-secret\"" +
                "    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"" +
                "    oauth.fallback.username.claim=\"client_id\"" +
                "    oauth.fallback.username.prefix=\"service-account-\"" +
                "    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTPLAIN listener
        configMap.put("listener.name.jwtplain.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.jwtplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required" +
                "    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"" +
                "    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"" +
                "    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"" +
                "    oauth.fallback.username.claim=\"client_id\"" +
                "    oauth.fallback.username.prefix=\"service-account-\"" +
                "    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // INTROSPECTPLAIN listener
        configMap.put("listener.name.introspectplain.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.introspectplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required" +
                "    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"" +
                "    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"" +
                "    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"" +
                "    oauth.client.id=\"kafka\"" +
                "    oauth.client.secret=\"kafka-secret\"" +
                "    oauth.fallback.username.claim=\"client_id\"" +
                "    oauth.fallback.username.prefix=\"service-account-\"" +
                "    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // JWTREFRESH listener
        configMap.put("listener.name.jwtrefresh.sasl.enabled.mechanisms", "OAUTHBEARER");
        configMap.put("listener.name.jwtrefresh.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required" +
                "    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"" +
                "    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"" +
                "    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"" +
                "    oauth.client.id=\"kafka\"" +
                "    oauth.client.secret=\"kafka-secret\"" +
                "    oauth.jwks.refresh.min.pause.seconds=\"2\"" +
                "    oauth.fallback.username.claim=\"client_id\"" +
                "    oauth.fallback.username.prefix=\"service-account-\"" +
                "    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtrefresh.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        // Enable re-authentication
        configMap.put("listener.name.jwtrefresh.oauthbearer.connections.max.reauth.ms", "3600000");

        // PLAIN listener
        configMap.put("listener.name.plain.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.plain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required" +
                "    username=\"admin\"" +
                "    password=\"admin-password\"" +
                "    user_admin=\"admin-password\"" +
                "    user_bobby=\"bobby-secret\" ;");

        // SCRAM listener
        configMap.put("listener.name.scram.sasl.enabled.mechanisms", "SCRAM-SHA-512");
        configMap.put("listener.name.scram.scram-sha-512.sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required    username=\"admin\"    password=\"admin-secret\" ;");

        // Authorizer configuration
        configMap.put("authorizer.class.name", "io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer");
        configMap.put("strimzi.authorization.token.endpoint.uri", "http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token");
        configMap.put("strimzi.authorization.client.id", "kafka");
        configMap.put("strimzi.authorization.client.secret", "kafka-secret");
        configMap.put("strimzi.authorization.kafka.cluster.name", "my-cluster");
        configMap.put("strimzi.authorization.delegate.to.kafka.acl", "true");
        configMap.put("strimzi.authorization.read.timeout.seconds", "45");
        configMap.put("strimzi.authorization.grants.refresh.pool.size", "4");
        configMap.put("strimzi.authorization.grants.refresh.period.seconds", "10");
        configMap.put("strimzi.authorization.http.retries", "1");
        configMap.put("strimzi.authorization.reuse.grants", "true");
        configMap.put("strimzi.authorization.enable.metrics", "true");

        // Super users
        configMap.put("super.users", "User:admin;User:service-account-kafka");

        return configMap;
    }
}
