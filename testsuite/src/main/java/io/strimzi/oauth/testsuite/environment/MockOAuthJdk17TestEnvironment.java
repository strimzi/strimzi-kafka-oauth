/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.test.container.OAuthKafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test environment for mockoauth-jdk17-tests module.
 * Manages Docker containers for the mock OAuth server and Kafka broker.
 */
public class MockOAuthJdk17TestEnvironment {

    private Network network;
    private GenericContainer<?> mockoauth;
    private OAuthKafkaContainer kafka;

    @SuppressWarnings("resource")
    public void start() {
        network = Network.newNetwork();

        // Start mock OAuth server with dynamic port mapping
        mockoauth = TestContainerFactory.createMockOAuth(network);
        mockoauth.start();

        // Publish mapped ports so test code can reach the mock OAuth server
        TestContainerFactory.publishMockOAuthPorts(mockoauth);

        // Start Kafka broker
        kafka = TestContainerFactory.createKafkaBase(network);
        kafka.withCopyDirToContainer(Path.of("target/kafka/certs").toAbsolutePath(), "/opt/kafka/config/strimzi/certs");
        kafka.withMetrics("/opt/kafka/config/strimzi/metrics-config.yml");
        kafka.waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaRaftServer\\).*", 1)
                .withStartupTimeout(Duration.ofSeconds(30)));

        // Set Kafka properties
        Map<String, String> kafkaConfig = new LinkedHashMap<>();

        // KRaft base properties
        TestContainerFactory.addKRaftBaseConfig(kafkaConfig);

        kafkaConfig.put("listeners", "CONTROLLER://kafka:9091,JWT://kafka:9092,INTROSPECT://kafka:9093,JWTPLAIN://kafka:9094,PLAIN://kafka:9095,INTROSPECTTIMEOUT://kafka:9096,FAILINGINTROSPECT://kafka:9097,FAILINGJWT://kafka:9098");
        kafkaConfig.put("advertised.listeners", "JWT://localhost:9092,INTROSPECT://localhost:9093,JWTPLAIN://localhost:9094,PLAIN://localhost:9095,INTROSPECTTIMEOUT://localhost:9096,FAILINGINTROSPECT://localhost:9097,FAILINGJWT://localhost:9098");
        kafkaConfig.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,PLAIN:SASL_PLAINTEXT,INTROSPECTTIMEOUT:SASL_PLAINTEXT,FAILINGINTROSPECT:SASL_PLAINTEXT,FAILINGJWT:SASL_PLAINTEXT");
        kafkaConfig.put("sasl.enabled.mechanisms", "OAUTHBEARER");

        kafkaConfig.put("inter.broker.listener.name", "JWT");
        kafkaConfig.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // INTROSPECT listener
        kafkaConfig.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"INTROSPECT\" oauth.introspection.endpoint.uri=\"https://mockoauth:8090/introspect\" oauth.client.id=\"unused\" oauth.client.secret=\"unused-secret\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWT listener
        kafkaConfig.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"JWT\" oauth.fail.fast=\"false\" oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\" oauth.jwks.refresh.seconds=\"10\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" oauth.check.access.token.type=\"false\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTPLAIN listener
        kafkaConfig.put("listener.name.jwtplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        kafkaConfig.put("listener.name.jwtplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"JWTPLAIN\" oauth.fail.fast=\"false\" oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.jwtplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        kafkaConfig.put("listener.name.jwtplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required oauth.config.id=\"JWTPLAIN\" oauth.token.endpoint.uri=\"https://mockoauth:8090/token\" oauth.fail.fast=\"false\" oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.jwtplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // PLAIN listener
        kafkaConfig.put("listener.name.plain.sasl.enabled.mechanisms", "PLAIN");
        kafkaConfig.put("listener.name.plain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"admin-password\" user_admin=\"admin-password\" ;");

        // INTROSPECTTIMEOUT listener
        kafkaConfig.put("listener.name.introspecttimeout.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"INTROSPECTTIMEOUT\" oauth.connect.timeout.seconds=\"5\" oauth.introspection.endpoint.uri=\"https://mockoauth:8090/introspect\" oauth.client.id=\"kafka\" oauth.client.secret=\"kafka-secret\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.introspecttimeout.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // FAILINGINTROSPECT listener
        kafkaConfig.put("listener.name.failingintrospect.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        kafkaConfig.put("listener.name.failingintrospect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"FAILINGINTROSPECT\" oauth.introspection.endpoint.uri=\"https://mockoauth:8090/failing_introspect\" oauth.userinfo.endpoint.uri=\"https://mockoauth:8090/failing_userinfo\" oauth.username.claim=\"uid\" oauth.client.id=\"kafka\" oauth.client.secret=\"kafka-secret\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" oauth.http.retries=\"1\" oauth.http.retry.pause.millis=\"3000\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.failingintrospect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        kafkaConfig.put("listener.name.failingintrospect.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required oauth.config.id=\"FAILINGINTROSPECT\" oauth.token.endpoint.uri=\"https://mockoauth:8090/failing_token\" oauth.introspection.endpoint.uri=\"https://mockoauth:8090/failing_introspect\" oauth.userinfo.endpoint.uri=\"https://mockoauth:8090/failing_userinfo\" oauth.username.claim=\"uid\" oauth.client.id=\"kafka\" oauth.client.secret=\"kafka-secret\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" oauth.http.retries=\"1\" oauth.http.retry.pause.millis=\"3000\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.failingintrospect.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // FAILINGJWT listener
        kafkaConfig.put("listener.name.failingjwt.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        kafkaConfig.put("listener.name.failingjwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.config.id=\"FAILINGJWT\" oauth.fail.fast=\"false\" oauth.check.access.token.type=\"false\" oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\" oauth.jwks.refresh.seconds=\"10\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.failingjwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        kafkaConfig.put("listener.name.failingjwt.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required oauth.config.id=\"FAILINGJWT\" oauth.fail.fast=\"false\" oauth.check.access.token.type=\"false\" oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\" oauth.jwks.refresh.seconds=\"10\" oauth.valid.issuer.uri=\"https://mockoauth:8090\" oauth.token.endpoint.uri=\"https://mockoauth:8090/failing_token\" oauth.http.retries=\"1\" oauth.http.retry.pause.millis=\"3000\" unsecuredLoginStringClaim_sub=\"admin\" ;");
        kafkaConfig.put("listener.name.failingjwt.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        kafka.withKafkaConfigurationMap(kafkaConfig);

        // OAuth env vars (NOT Kafka properties)
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_LOCATION", "/opt/kafka/config/strimzi/certs/ca-truststore.p12");
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_PASSWORD", "changeit");
        kafka.addEnv("OAUTH_SSL_TRUSTSTORE_TYPE", "pkcs12");
        kafka.addEnv("OAUTH_CONNECT_TIMEOUT_SECONDS", "10");
        kafka.addEnv("OAUTH_READ_TIMEOUT_SECONDS", "10");
        kafka.addEnv("OAUTH_ENABLE_METRICS", "true");
        kafka.addEnv("STRIMZI_OAUTH_METRIC_REPORTERS", "org.apache.kafka.common.metrics.JmxReporter");

        // Fixed exposed ports for Kafka listeners
        kafka.addFixedExposedPort(9091, 9091);
        kafka.addFixedExposedPort(9092, 9092);
        kafka.addFixedExposedPort(9093, 9093);
        kafka.addFixedExposedPort(9094, 9094);
        kafka.addFixedExposedPort(9095, 9095);
        kafka.addFixedExposedPort(9096, 9096);
        kafka.addFixedExposedPort(9097, 9097);
        kafka.addFixedExposedPort(9098, 9098);

        kafka.start();
    }

    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
        if (mockoauth != null) {
            mockoauth.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    public List<GenericContainer<?>> getContainers() {
        List<GenericContainer<?>> containers = new ArrayList<>();
        if (mockoauth != null) {
            containers.add(mockoauth);
        }
        if (kafka != null) {
            containers.add(kafka);
        }
        return containers;
    }
}
