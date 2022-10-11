/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.testsuite.oauth.metrics.Metrics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

public class Common {

    static final String WWW_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    static String getJaasConfigOptionsString(Map<String, String> options) {
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

    static void setCommonProducerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        p.setProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        p.setProperty(ProducerConfig.RETRIES_CONFIG, "0");
    }

    public static Properties buildConsumerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    static Properties buildCommonConfigOAuthBearer(Map<String, String> oauthConfig) {
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

    public static Properties buildConsumerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonConsumerProperties(kafkaBootstrap, p);
        return p;
    }

    static void setCommonConsumerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    }

    static Properties buildCommonConfigPlain(Map<String, String> plainConfig) {
        String configOptions = getJaasConfigOptionsString(plainConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "PLAIN");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " + configOptions + " ;");
        return p;
    }

    static Properties buildCommonConfigScram(Map<String, String> scramConfig) {
        String configOptions = getJaasConfigOptionsString(scramConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required " + configOptions + " ;");
        return p;
    }

    static String loginWithUsernameForRefreshToken(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

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

    static String loginWithUsernamePassword(URI tokenEndpointUri, String username, String password, String clientId) throws IOException {

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

    /**
     * Get response from prometheus endpoint as a map of key:value pairs
     * We expect the response to be a 'well formed' key=value document in the sense that each line contains a '=' sign
     *
     * @param metricsEndpointUri The endpoint used to fetch metrics
     * @return Metrics object
     * @throws IOException if an error occurs while getting or parsing the metrics endpoint content
     */
    public static Metrics getPrometheusMetrics(URI metricsEndpointUri) throws IOException {
        String response = HttpUtil.get(metricsEndpointUri, null, null, null, String.class);

        Metrics metrics = new Metrics();
        //Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new StringReader(response))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                String key = null;
                Map<String, String> attrs = new LinkedHashMap<>();

                int endPos;
                int pos = line.indexOf('{');

                if (pos != -1) {
                    key = line.substring(0, pos);

                    endPos = line.lastIndexOf("}");
                    String attrsPart = line.substring(pos + 1, endPos);
                    String[] attrsArray = attrsPart.split(",");

                    for (String attr : attrsArray) {
                        String[] keyVal = attr.split("=");
                        if (keyVal.length != 2) {
                            // skip mis-parsed attribute values due to ',' inside a quoted value
                            // the entries we are interested in should never have comma in the attribute
                            continue;
                        }
                        attrs.put(keyVal[0], keyVal[1].substring(1, keyVal[1].length() - 1));
                    }
                }
                endPos = line.lastIndexOf(" ");
                if (key == null) {
                    key = line.substring(0, endPos);
                }
                String value = line.substring(endPos + 1);
                metrics.addMetric(key, attrs, unquote(value));
            }
        }
        return metrics;
    }

    private static String unquote(String value) {
        return value.startsWith("\"") ?
                value.substring(1, value.length() - 1) :
                value;
    }

    public static void changeAuthServerMode(String resource, String mode) throws IOException {
        String result = HttpUtil.post(URI.create("http://mockoauth:8091/admin/" + resource + "?mode=" + mode), null, "text/plain", "", String.class);
        Assert.assertEquals("admin server response should be ", mode.toUpperCase(Locale.ROOT), result);
    }

    public static void createOAuthClient(String clientId, String secret) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/clients"),
                null,
                "application/json",
                "{\"clientId\": \"" + clientId + "\", \"secret\": \"" + secret + "\"}", String.class);
    }

    public static void createOAuthClientWithAssertion(String clientId, String clientAssertion) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/clients"),
                null,
                "application/json",
                "{\"clientId\": \"" + clientId + "\", \"clientAssertion\": \"" + clientAssertion + "\"}", String.class);
    }

    public static void createOAuthUser(String username, String password) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}", String.class);
    }

    public static void revokeToken(String token) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/revocations"),
                null,
                "application/json",
                "{\"token\": \"" + token + "\"}", String.class);
    }

    public static Metrics reloadMetrics() throws IOException {
        return getPrometheusMetrics(URI.create("http://kafka:9404/metrics"));
    }

    public static String getProjectRoot() {
        String cwd = System.getProperty("user.dir");
        Path path = Paths.get(cwd);
        if (path.endsWith("mockoauth-tests") && Files.exists(path.resolve("../docker"))) {
            return path.toAbsolutePath().toString();
        } else if (path.endsWith("strimzi-kafka-oauth") && Files.exists(path.resolve("testsuite/docker")) && Files.exists(path.resolve("testsuite/mockoauth-tests")))  {
            return path.resolve("testsuite/mockoauth-tests").toAbsolutePath().toString();
        }
        return cwd;
    }
}
