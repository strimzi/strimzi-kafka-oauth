/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.hydra;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildConsumerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.consumeAndAssert;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.produceMessage;

/**
 * Tests for OAuth authentication using Hydra
 * <p>
 * This test assumes there are multiple listeners configured with OAUTHBEARER support, but each configured differently
 * - configured with different options, and even a different auth server host.
 * <p>
 * There should be no authorization configured on the Kafka broker.
 */
@OAuthEnvironment(
    authServer = AuthServer.HYDRA,
    kafka = @KafkaConfig(
        clientId = "kafka-broker",
        clientSecret = "kafka-broker-secret",
        oauthProperties = {
            "oauth.introspection.endpoint.uri=https://hydra:4445/admin/oauth2/introspect",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "oauth.valid.issuer.uri=https://hydra:4444/",
            "oauth.token.endpoint.uri=https://hydra:4444/oauth2/token",
            "oauth.check.access.token.type=false",
            "oauth.access.token.is.jwt=false",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class HydraAuthenticationIT {

    OAuthEnvironmentExtension env;

    @Test
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PKCS12)
    public void testWithPKCS() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PKCS12 - opaque access token with introspect validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    @Test
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PEM)
    public void testWithPemFromFile() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "docker/certificates/ca.crt");
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "PEM");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PEM from file - opaque access token with introspect validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    @Test
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.PEM)
    public void testWithPemFromString() throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_CERTIFICATES, new String(Files.readAllBytes(Paths.get("docker/certificates/ca.crt"))));
        //defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, null);
        defaults.setProperty(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "PEM");

        try {
            ConfigProperties.resolveAndExportToSystemProperties(defaults);

            opaqueAccessTokenWithIntrospectValidationTest("PEM from string - opaque access token with introspect validation test");
        } finally {
            clearSystemProperties(defaults);
        }
    }

    @Test
    @Tag(TestTags.AUTHENTICATION)
    @Tag(TestTags.JWT)
    @KafkaConfig(
        clientId = "kafka-broker",
        clientSecret = "kafka-broker-secret",
        oauthProperties = {
            "oauth.jwks.endpoint.uri=https://hydra-jwt:4454/.well-known/jwks.json",
            "oauth.valid.issuer.uri=https://hydra-jwt:4454/",
            "oauth.token.endpoint.uri=https://hydra-jwt:4454/oauth2/token",
            "oauth.check.access.token.type=false",
            "oauth.client.id=kafka-broker",
            "oauth.client.secret=kafka-broker-secret",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
    public void testClientCredentialsWithJwtValidation() throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getHydraJwtHostPort();
        final String tokenEndpointUri = "https://" + hostPort + "/oauth2/token";

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_ID, "kafka-producer-client");
        oauthConfig.put(ClientConfig.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "target/kafka/certs/ca-truststore.p12");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        oauthConfig.put(ClientConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");

        String topic = "HydraAuthenticationTest-clientCredentialsWithJwtValidation";

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, "The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        consumeAndAssert(consumerProps, topic, "The Message");
    }

    private void opaqueAccessTokenWithIntrospectValidationTest(String title) throws Exception {
        final String kafkaBootstrap = env.getBootstrapServers();
        final String hostPort = env.getHydraHostPort();

        final String tokenEndpointUri = "https://" + hostPort + "/oauth2/token";

        final String clientId = "kafka-producer-client";
        final String clientSecret = "kafka-producer-client-secret";

        // first, request access token using client id and secret
        TokenInfo info = OAuthAuthenticator.loginWithClientSecret(URI.create(tokenEndpointUri),
            ConfigUtil.createSSLFactory(new ClientConfig()),
            null, clientId, clientSecret, true, null, null, true);

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, info.token());
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "false");

        String topic = "HydraAuthenticationTest-" + toCamelCase(title);

        Properties producerProps = buildProducerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        produceMessage(producerProps, topic, "The Message");

        Properties consumerProps = buildConsumerConfigOAuthBearer(kafkaBootstrap, oauthConfig);
        consumeAndAssert(consumerProps, topic, "The Message");
    }

    private static void clearSystemProperties(Properties defaults) {
        Properties p = new ConfigProperties(defaults).resolveTo(new Properties());
        for (Object key : p.keySet()) {
            System.clearProperty(key.toString());
        }
    }

    static String toCamelCase(String title) {
        String[] words = title.split("[\\s]+");
        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].toLowerCase(Locale.ENGLISH);
            if (i > 0) {
                words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
            }
        }
        return String.join("", words);
    }
}
