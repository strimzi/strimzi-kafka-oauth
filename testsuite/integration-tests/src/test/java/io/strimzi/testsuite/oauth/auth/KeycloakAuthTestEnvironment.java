/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import io.strimzi.test.container.OAuthKafkaContainer;
import io.strimzi.testsuite.oauth.support.TestContainerFactory;
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
 * Manages the Docker containers for Keycloak authentication tests using programmatic Testcontainers.
 */
public class KeycloakAuthTestEnvironment {

    private Network network;
    private GenericContainer<?> keycloak;
    private OAuthKafkaContainer kafka;

    /**
     * Start all the containers.
     */
    public void start() {
        network = Network.newNetwork();

        // Start Keycloak
        keycloak = TestContainerFactory.createKeycloak(network);
        keycloak.start();
        TestContainerFactory.publishKeycloakPort(keycloak);

        // Build Kafka configuration map from docker-compose KAFKA_* env vars
        Map<String, String> kafkaConfigMap = buildKafkaConfigMap();

        kafka = TestContainerFactory.createKafkaBase(network);
        kafka.withCopyToContainer(
                MountableFile.forHostPath(Path.of("../common/target/classes").toAbsolutePath().toString()),
                "/opt/kafka/libs/strimzi/reporters");
        kafka.withMetrics("/opt/kafka/config/strimzi/metrics-config.yml");
        kafka.withKafkaConfigurationMap(kafkaConfigMap);

        // OAuth env vars - set via addEnv(), NOT kafkaConfigurationMap
        kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
        kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
        kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter,io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        kafka.addEnv("CLASSPATH", "/opt/kafka/libs/strimzi/reporters");

        // Fixed exposed ports for Kafka listeners
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

    /**
     * Get the Keycloak host:port for test client connections.
     *
     * @return The host:port string for the Keycloak container
     */
    public String getKeycloakHostPort() {
        return keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    private static Map<String, String> buildKafkaConfigMap() {
        Map<String, String> configMap = new HashMap<>();

        // KRaft base properties
        TestContainerFactory.addKRaftBaseConfig(configMap);

        // Listeners
        configMap.put("listeners", "CONTROLLER://kafka:9091,JWT://kafka:9092,INTROSPECT://kafka:9093,AUDIENCE://kafka:9094,AUDIENCEINTROSPECT://kafka:9095,JWTPLAIN://kafka:9096,INTROSPECTPLAIN://kafka:9097,CUSTOM://kafka:9098,CUSTOMINTROSPECT://kafka:9099,PLAIN://kafka:9100,SCRAM://kafka:9101,FLOOD://kafka:9102,JWTPLAINWITHOUTCC://kafka:9103,FORGE://kafka:9104");
        configMap.put("advertised.listeners", "JWT://localhost:9092,INTROSPECT://localhost:9093,AUDIENCE://localhost:9094,AUDIENCEINTROSPECT://localhost:9095,JWTPLAIN://localhost:9096,INTROSPECTPLAIN://localhost:9097,CUSTOM://localhost:9098,CUSTOMINTROSPECT://localhost:9099,PLAIN://localhost:9100,SCRAM://localhost:9101,FLOOD://localhost:9102,JWTPLAINWITHOUTCC://localhost:9103,FORGE://localhost:9104");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,AUDIENCE:SASL_PLAINTEXT,AUDIENCEINTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,INTROSPECTPLAIN:SASL_PLAINTEXT,CUSTOM:SASL_PLAINTEXT,CUSTOMINTROSPECT:SASL_PLAINTEXT,PLAIN:SASL_PLAINTEXT,SCRAM:SASL_PLAINTEXT,FLOOD:SASL_PLAINTEXT,JWTPLAINWITHOUTCC:SASL_PLAINTEXT,FORGE:SASL_PLAINTEXT");
        configMap.put("sasl.enabled.mechanisms", "OAUTHBEARER");

        // Inter-broker
        configMap.put("inter.broker.listener.name", "INTROSPECT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // INTROSPECT listener
        configMap.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"INTROSPECT\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/demo/protocol/openid-connect/token/introspect\"    oauth.client.id=\"kafka-broker\"    oauth.client.secret=\"kafka-broker-secret\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo\"    oauth.groups.claim=\"$.groups\"     oauth.token.endpoint.uri=\"http://keycloak:8080/realms/demo/protocol/openid-connect/token\"    oauth.fallback.username.claim=\"username\" ;");
        configMap.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.introspect.oauthbearer.sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        // JWT listener
        configMap.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"JWT\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/demo-ec/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo-ec\"    unsecuredLoginStringClaim_sub=\"admin\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\" ;");
        configMap.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // AUDIENCE listener
        configMap.put("listener.name.audience.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"AUDIENCE\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.check.audience=\"true\"    oauth.client.id=\"kafka\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.audience.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // AUDIENCEINTROSPECT listener
        configMap.put("listener.name.audienceintrospect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"AUDIENCEINTROSPECT\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.check.audience=\"true\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.audienceintrospect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTPLAIN listener
        configMap.put("listener.name.jwtplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"JWTPLAIN\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.jwtplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"JWTPLAIN\"    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // INTROSPECTPLAIN listener
        configMap.put("listener.name.introspectplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.introspectplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"INTROSPECTPLAIN\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.introspectplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"INTROSPECTPLAIN\"    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // CUSTOM listener
        configMap.put("listener.name.custom.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"CUSTOM\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.check.issuer=\"false\"    oauth.check.access.token.type=\"false\"    oauth.custom.claim.check=\"@.typ == 'Bearer' && @.iss == 'http://keycloak:8080/realms/kafka-authz' && 'kafka' in @.aud && 'kafka-user' in @.resource_access.kafka.roles\"    oauth.groups.claim=\"$.realm_access.roles\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.custom.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // CUSTOMINTROSPECT listener
        configMap.put("listener.name.customintrospect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"CUSTOMINTROSPECT\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.check.issuer=\"false\"    oauth.check.access.token.type=\"false\"    oauth.custom.claim.check=\"@.typ == 'Bearer' && @.iss == 'http://keycloak:8080/realms/kafka-authz' && 'kafka' in @.aud && 'kafka-user' in @.resource_access.kafka.roles\"    oauth.groups.claim=\"$['resource_access']['kafka']['roles']\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.customintrospect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // PLAIN listener
        configMap.put("listener.name.plain.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.plain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    username=\"admin\"    password=\"admin-password\"    user_admin=\"admin-password\"    user_bobby=\"bobby-secret\" ;");

        // SCRAM listener
        configMap.put("listener.name.scram.sasl.enabled.mechanisms", "SCRAM-SHA-512");
        configMap.put("listener.name.scram.scram-sha-512.sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required    username=\"admin\"    password=\"admin-secret\" ;");

        // FLOOD listener
        configMap.put("listener.name.flood.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.flood.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"FLOOD\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/flood/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/flood\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.flood.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.flood.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"FLOOD\"    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/flood/protocol/openid-connect/token\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/flood/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/flood\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.flood.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // JWTPLAINWITHOUTCC listener
        configMap.put("listener.name.jwtplainwithoutcc.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.jwtplainwithoutcc.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"JWTPLAINWITHOUTCC\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplainwithoutcc.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // FORGE listener
        configMap.put("listener.name.forge.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"FORGE\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/forge/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/forge\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.forge.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // Metrics properties
        configMap.put("metrics.context.test.label", "testvalue");
        configMap.put("metrics.num.samples", "3");
        configMap.put("metrics.recording.level", "DEBUG");
        configMap.put("metrics.sample.window.ms", "15000");

        return configMap;
    }
}
