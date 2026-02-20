/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides Kafka listener configuration maps for each {@link KafkaPreset}.
 * <p>
 * Each method is extracted from the corresponding {@code *TestEnvironment.buildKafkaConfigMap()} method.
 */
public final class KafkaPresetConfig {

    private KafkaPresetConfig() {
    }

    /**
     * Get the Kafka configuration map for the given preset.
     *
     * @param preset The Kafka listener preset
     * @return A configuration map for Kafka
     */
    public static Map<String, String> getConfig(KafkaPreset preset) {
        switch (preset) {
            case MOCK_OAUTH: return buildMockOAuthConfig();
            case KEYCLOAK_AUTH: return buildKeycloakAuthConfig();
            case KEYCLOAK_AUTHZ: return buildKeycloakAuthzConfig();
            case KEYCLOAK_ERRORS: return buildKeycloakErrorsConfig();
            case HYDRA: return buildHydraConfig();
            default: return new HashMap<>();
        }
    }

    private static Map<String, String> buildMockOAuthConfig() {
        Map<String, String> configMap = new HashMap<>();

        // KRaft base properties
        TestContainerFactory.addKRaftBaseConfig(configMap);

        // Listeners
        configMap.put("listeners", "CONTROLLER://kafka:9091,JWT://kafka:9092,INTROSPECT://kafka:9093,JWTPLAIN://kafka:9094,PLAIN://kafka:9095,INTROSPECTTIMEOUT://kafka:9096,FAILINGINTROSPECT://kafka:9097,FAILINGJWT://kafka:9098");
        configMap.put("advertised.listeners",
                "JWT://localhost:9092,INTROSPECT://localhost:9093,JWTPLAIN://localhost:9094," +
                "PLAIN://localhost:9095,INTROSPECTTIMEOUT://localhost:9096," +
                "FAILINGINTROSPECT://localhost:9097,FAILINGJWT://localhost:9098");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,PLAIN:SASL_PLAINTEXT,INTROSPECTTIMEOUT:SASL_PLAINTEXT,FAILINGINTROSPECT:SASL_PLAINTEXT,FAILINGJWT:SASL_PLAINTEXT");
        configMap.put("sasl.enabled.mechanisms", "OAUTHBEARER");

        // Inter-broker
        configMap.put("inter.broker.listener.name", "JWT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

        // INTROSPECT listener
        configMap.put("listener.name.introspect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"INTROSPECT\"    oauth.introspection.endpoint.uri=\"https://mockoauth:8090/introspect\"    oauth.client.id=\"unused\"    oauth.client.secret=\"unused-secret\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWT listener
        configMap.put("listener.name.jwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"JWT\"    oauth.fail.fast=\"false\"    oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\"    oauth.jwks.refresh.seconds=\"10\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    oauth.check.access.token.type=\"false\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // JWTPLAIN listener
        configMap.put("listener.name.jwtplain.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"JWTPLAIN\"    oauth.fail.fast=\"false\"    oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.jwtplain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"JWTPLAIN\"    oauth.token.endpoint.uri=\"https://mockoauth:8090/token\"    oauth.fail.fast=\"false\"    oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.jwtplain.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // PLAIN listener
        configMap.put("listener.name.plain.sasl.enabled.mechanisms", "PLAIN");
        configMap.put("listener.name.plain.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    username=\"admin\"    password=\"admin-password\"    user_admin=\"admin-password\" ;");

        // INTROSPECTTIMEOUT listener
        configMap.put("listener.name.introspecttimeout.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"INTROSPECTTIMEOUT\"    oauth.connect.timeout.seconds=\"5\"    oauth.introspection.endpoint.uri=\"https://mockoauth:8090/introspect\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.introspecttimeout.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");

        // FAILINGINTROSPECT listener
        configMap.put("listener.name.failingintrospect.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.failingintrospect.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"FAILINGINTROSPECT\"    oauth.introspection.endpoint.uri=\"https://mockoauth:8090/failing_introspect\"    oauth.userinfo.endpoint.uri=\"https://mockoauth:8090/failing_userinfo\"    oauth.username.claim=\"uid\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    oauth.http.retries=\"1\"    oauth.http.retry.pause.millis=\"3000\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.failingintrospect.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.failingintrospect.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"FAILINGINTROSPECT\"    oauth.token.endpoint.uri=\"https://mockoauth:8090/failing_token\"    oauth.introspection.endpoint.uri=\"https://mockoauth:8090/failing_introspect\"    oauth.userinfo.endpoint.uri=\"https://mockoauth:8090/failing_userinfo\"    oauth.username.claim=\"uid\"    oauth.client.id=\"kafka\"    oauth.client.secret=\"kafka-secret\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    oauth.http.retries=\"1\"    oauth.http.retry.pause.millis=\"3000\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.failingintrospect.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        // FAILINGJWT listener
        configMap.put("listener.name.failingjwt.sasl.enabled.mechanisms", "OAUTHBEARER,PLAIN");
        configMap.put("listener.name.failingjwt.oauthbearer.sasl.jaas.config",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required    oauth.config.id=\"FAILINGJWT\"    oauth.fail.fast=\"false\"    oauth.check.access.token.type=\"false\"    oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\"    oauth.jwks.refresh.seconds=\"10\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.failingjwt.oauthbearer.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler");
        configMap.put("listener.name.failingjwt.plain.sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required    oauth.config.id=\"FAILINGJWT\"    oauth.fail.fast=\"false\"    oauth.check.access.token.type=\"false\"    oauth.jwks.endpoint.uri=\"https://mockoauth:8090/jwks\"    oauth.jwks.refresh.seconds=\"10\"    oauth.valid.issuer.uri=\"https://mockoauth:8090\"    oauth.token.endpoint.uri=\"https://mockoauth:8090/failing_token\"    oauth.http.retries=\"1\"    oauth.http.retry.pause.millis=\"3000\"    unsecuredLoginStringClaim_sub=\"admin\" ;");
        configMap.put("listener.name.failingjwt.plain.sasl.server.callback.handler.class",
                "io.strimzi.kafka.oauth.server.plain.JaasServerOauthOverPlainValidatorCallbackHandler");

        return configMap;
    }

    private static Map<String, String> buildKeycloakAuthConfig() {
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

    private static Map<String, String> buildKeycloakAuthzConfig() {
        Map<String, String> configMap = new HashMap<>();

        // KRaft base properties
        TestContainerFactory.addKRaftBaseConfig(configMap);

        // Listeners
        configMap.put("listeners", "CONTROLLER://0.0.0.0:9091,JWT://0.0.0.0:9092,INTROSPECT://0.0.0.0:9093,JWTPLAIN://0.0.0.0:9094,INTROSPECTPLAIN://0.0.0.0:9095,JWTREFRESH://0.0.0.0:9096,PLAIN://0.0.0.0:9100,SCRAM://0.0.0.0:9101");
        configMap.put("advertised.listeners", "JWT://localhost:9092,INTROSPECT://localhost:9093,JWTPLAIN://localhost:9094,INTROSPECTPLAIN://localhost:9095,JWTREFRESH://localhost:9096,PLAIN://localhost:9100,SCRAM://localhost:9101");
        configMap.put("listener.security.protocol.map", "CONTROLLER:SASL_PLAINTEXT,JWT:SASL_PLAINTEXT,INTROSPECT:SASL_PLAINTEXT,JWTPLAIN:SASL_PLAINTEXT,INTROSPECTPLAIN:SASL_PLAINTEXT,JWTREFRESH:SASL_PLAINTEXT,PLAIN:SASL_PLAINTEXT,SCRAM:SASL_PLAINTEXT");

        // Inter-broker
        configMap.put("inter.broker.listener.name", "JWT");
        configMap.put("sasl.mechanism.inter.broker.protocol", "OAUTHBEARER");

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

    private static Map<String, String> buildKeycloakErrorsConfig() {
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

    private static Map<String, String> buildHydraConfig() {
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
