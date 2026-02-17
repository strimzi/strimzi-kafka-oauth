/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.errors;

import io.strimzi.test.container.OAuthKafkaContainer;
import io.strimzi.testsuite.oauth.support.TestContainerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Docker containers for Keycloak Errors tests using programmatic Testcontainers.
 */
public class KeycloakErrorsTestEnvironment {

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
        kafka.withKafkaConfigurationMap(kafkaConfigMap);

        // OAuth env vars - set via addEnv(), NOT kafkaConfigurationMap
        kafka.addEnv("OAUTH_USERNAME_CLAIM", "preferred_username");
        kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "20");

        // Fixed exposed ports for Kafka listeners
        kafka.addFixedExposedPort(9091, 9091);
        kafka.addFixedExposedPort(9201, 9201);
        kafka.addFixedExposedPort(9202, 9202);
        kafka.addFixedExposedPort(9203, 9203);
        kafka.addFixedExposedPort(9204, 9204);
        kafka.addFixedExposedPort(9205, 9205);
        kafka.addFixedExposedPort(9206, 9206);
        kafka.addFixedExposedPort(9207, 9207);
        kafka.addFixedExposedPort(9208, 9208);

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
     * Get the Keycloak container.
     *
     * @return The Keycloak GenericContainer
     */
    public GenericContainer<?> getKeycloak() {
        return keycloak;
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
        configMap.put("listeners", "CONTROLLER://kafka:9091,JWT://kafka:9201,INTROSPECT://kafka:9202,JWTPLAIN://kafka:9203,INTROSPECTPLAIN://kafka:9204,EXPIRETEST://kafka:9205,JWTCANTCONNECT://kafka:9206,INTROSPECTCANTCONNECT://kafka:9207,INTROSPECTTIMEOUT://kafka:9208");
        configMap.put("advertised.listeners", "JWT://localhost:9201,INTROSPECT://localhost:9202,JWTPLAIN://localhost:9203,INTROSPECTPLAIN://localhost:9204,EXPIRETEST://localhost:9205,JWTCANTCONNECT://localhost:9206,INTROSPECTCANTCONNECT://localhost:9207,INTROSPECTTIMEOUT://localhost:9208");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,INTROSPECTPLAIN:SASL_PLAINTEXT,EXPIRETEST:SASL_PLAINTEXT,JWTCANTCONNECT:SASL_PLAINTEXT,INTROSPECTCANTCONNECT:SASL_PLAINTEXT,INTROSPECTTIMEOUT:SASL_PLAINTEXT");
        configMap.put("sasl.enabled.mechanisms", "OAUTHBEARER");

        // Inter-broker
        configMap.put("inter.broker.listener.name", "INTROSPECT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // INTROSPECT listener
        configMap.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/demo/protocol/openid-connect/token/introspect\"    oauth.client.id=\"kafka-broker\"    oauth.client.secret=\"kafka-broker-secret\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo\"    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/demo/protocol/openid-connect/token\"    oauth.fallback.username.claim=\"username\" ;");
        configMap.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.introspect.oauthbearer.sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        // JWT listener
        configMap.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/demo-ec/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/demo-ec\"    unsecuredLoginStringClaim_sub=\"admin\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\" ;");
        configMap.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTPLAIN listener
        configMap.put("listener.name.jwtplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.jwtplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // INTROSPECTPLAIN listener
        configMap.put("listener.name.introspectplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.introspectplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.introspectplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.token.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token\"    oauth.introspection.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token/introspect\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/kafka-authz\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // EXPIRETEST listener
        configMap.put("listener.name.expiretest.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/expiretest/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"http://keycloak:8080/realms/expiretest\"    oauth.fallback.username.claim=\"client_id\"    oauth.fallback.username.prefix=\"service-account-\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.expiretest.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTCANTCONNECT listener
        configMap.put("listener.name.jwtcantconnect.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.jwtcantconnect.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.token.endpoint.uri=\"http://keycloak:8081/realms/kafka-authz/protocol/openid-connect/token\"    oauth.jwks.endpoint.uri=\"http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/certs\"    oauth.valid.issuer.uri=\"https://whatever\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtcantconnect.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // INTROSPECTCANTCONNECT listener
        configMap.put("listener.name.introspectcantconnect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.introspection.endpoint.uri=\"http://keycloak:8081/realms/demo/protocol/openid-connect/token/introspect\"    oauth.client.id=\"kafka-broker\"    oauth.client.secret=\"kafka-broker-secret\"    oauth.valid.issuer.uri=\"https://whatever\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspectcantconnect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // INTROSPECTTIMEOUT listener
        configMap.put("listener.name.introspecttimeout.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.connect.timeout.seconds=\"5\"    oauth.introspection.endpoint.uri=\"http://172.0.0.221:8081/realms/demo/protocol/openid-connect/token/introspect\"    oauth.client.id=\"kafka-broker\"    oauth.client.secret=\"kafka-broker-secret\"    oauth.valid.issuer.uri=\"https://whatever\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspecttimeout.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        return configMap;
    }
}
