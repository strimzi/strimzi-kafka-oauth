/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.clients;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64encode;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

/**
 * Shared Kafka client configuration builders for OAuth/SASL authentication tests.
 */
public class KafkaClientsConfig {

    public static final String WWW_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private static final Logger log = LoggerFactory.getLogger(KafkaClientsConfig.class);

    public static String getJaasConfigOptionsString(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent: options.entrySet()) {
            sb.append(" ").append(ent.getKey()).append("=\"").append(ent.getValue()).append("\"");
        }
        return sb.toString();
    }

    public static Properties buildProducerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static void setCommonProducerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        p.setProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        p.setProperty(ProducerConfig.RETRIES_CONFIG, "0");

        // To ease debugging
        p.setProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "600000");
    }

    public static Properties buildConsumerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildCommonConfigOAuthBearer(Map<String, String> oauthConfig) {
        String configOptions = getJaasConfigOptionsString(oauthConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " + configOptions + " ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        return p;
    }

    public static Properties buildProducerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildProducerConfigScram(String kafkaBootstrap, Map<String, String> scramConfig) {
        Properties p = buildCommonConfigScram(scramConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildConsumerConfigScram(String kafkaBootstrap, Map<String, String> scramConfig) {
        Properties p = buildCommonConfigScram(scramConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    public static Properties buildConsumerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    public static void setCommonConsumerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    }

    public static Properties buildCommonConfigPlain(Map<String, String> plainConfig) {
        String configOptions = getJaasConfigOptionsString(plainConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "PLAIN");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " + configOptions + " ;");
        return p;
    }

    public static Properties buildCommonConfigScram(Map<String, String> scramConfig) {
        String configOptions = getJaasConfigOptionsString(scramConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required " + configOptions + " ;");
        return p;
    }

    public static String loginWithUsernameForRefreshToken(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                null,
                WWW_FORM_CONTENT_TYPE,
                "grant_type=password&username=" + username + "&password=" + password + "&client_id=" + clientId,
                JsonNode.class);

        JsonNode token = result.get("refresh_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no refresh_token");
        }
        return token.asText();
    }

    public static String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {
        return loginWithUsernamePassword(tokenEndpointUri, username, password, clientId, null);
    }

    public static String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId, String secret) throws IOException {

        String body = "grant_type=password&username=" + urlencode(username) +
                "&password=" + urlencode(password);

        String authorization = "Basic " + base64encode(clientId + ":" + (secret != null ? secret : ""));

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                authorization,
                WWW_FORM_CONTENT_TYPE,
                body,
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }

    /**
     * Get an access token from the token endpoint using client credentials by way of the OAuthAuthenticator.loginWithClientSecret method.
     *
     * @param tokenEndpoint The token endpoint
     * @param clientId The client ID
     * @param secret The client secret
     * @param truststorePath The path to the truststore for TLS connection
     * @param truststorePass The truststore password for TLS connection
     * @return The access token returned from the authorization server's token endpoint
     * @throws IOException Exception while sending the request
     */
    public static String loginWithClientSecret(String tokenEndpoint, String clientId, String secret, String truststorePath, String truststorePass) throws IOException {
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create(tokenEndpoint),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
                SSLUtil.createAnyHostHostnameVerifier(),
                clientId,
                secret,
                true,
                new PrincipalExtractor(),
                "all",
                true);

        return tokenInfo.token();
    }

    /**
     * Get an access token from the token endpoint using client credentials and additional body attributes.
     *
     * @param tokenEndpoint The token endpoint
     * @param clientId The client ID
     * @param secret The client secret
     * @param truststorePath The path to the truststore for TLS connection
     * @param truststorePass The truststore password for TLS connection
     * @param extraAttrs The string of url-encoded key=value pairs separated by '&' to be added to the body of the token request
     * @return The access token returned from the authorization server's token endpoint
     * @throws IOException Exception while sending the request
     */
    public static String loginWithClientSecretAndExtraAttrs(String tokenEndpoint, String clientId, String secret,
                                                             String truststorePath, String truststorePass, String extraAttrs) throws IOException {

        if (clientId == null) {
            throw new IllegalArgumentException("No clientId specified");
        }
        if (secret == null) {
            secret = "";
        }

        String authorization = "Basic " + base64encode(clientId + ':' + secret);

        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        if (extraAttrs != null) {
            body.append("&").append(extraAttrs);
        }
        JsonNode result = HttpUtil.post(URI.create(tokenEndpoint),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
                SSLUtil.createAnyHostHostnameVerifier(),
                authorization,
                WWW_FORM_CONTENT_TYPE,
                body.toString(),
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }

    public static String loginWithUsernameForRefreshToken(String tokenEndpointUri, String username, String password, String clientId, String truststorePath, String truststorePass) throws IOException {

        JsonNode result = HttpUtil.post(URI.create(tokenEndpointUri),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
                SSLUtil.createAnyHostHostnameVerifier(),
                null,
                WWW_FORM_CONTENT_TYPE,
                "grant_type=password&username=" + username + "&password=" + password + "&client_id=" + clientId,
                JsonNode.class);

        JsonNode token = result.get("refresh_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no refresh_token");
        }
        return token.asText();
    }

    /**
     * Login with username and password using client_id in the request body (not Basic Auth).
     * This is the variant needed by Keycloak authorization tests.
     */
    public static String loginWithUsernamePasswordInBody(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

        String body = "grant_type=password&username=" + urlencode(username) +
                "&password=" + urlencode(password) + "&client_id=" + urlencode(clientId);

        JsonNode result = HttpUtil.post(tokenEndpointUri,
                null,
                null,
                null,
                WWW_FORM_CONTENT_TYPE,
                body,
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }

    public static AdminClient buildAdminClientForPlain(String kafkaBootstrap, String user) {
        Map<String, String> plainConfig = new HashMap<>();
        plainConfig.put("username", user);
        plainConfig.put("password", user + "-password");
        Properties adminProps = buildProducerConfigPlain(kafkaBootstrap, plainConfig);
        return AdminClient.create(adminProps);
    }

    public static <K, V> ConsumerRecords<K, V> poll(Consumer<K, V> consumer) {
        ConsumerRecords<K, V> result = consumer.poll(Duration.ofSeconds(5));
        if (result.isEmpty()) {
            log.warn("No result after 5 seconds. Repeating ...");
            result = consumer.poll(Duration.ofSeconds(5));
        }
        return result;
    }
}
