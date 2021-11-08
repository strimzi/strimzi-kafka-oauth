/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

public class Common {

    static String getJaasConfigOptionsString(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> ent: options.entrySet()) {
            sb.append(" ").append(ent.getKey()).append("=\"").append(ent.getValue()).append("\"");
        }
        return sb.toString();
    }

    static Properties buildProducerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
        Properties p = buildCommonConfigOAuthBearer(oauthConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    static void setCommonProducerProperties(String kafkaBootstrap, Properties p) {
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        p.setProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "300000");
        p.setProperty(ProducerConfig.RETRIES_CONFIG, "0");
    }

    static Properties buildConsumerConfigOAuthBearer(String kafkaBootstrap, Map<String, String> oauthConfig) {
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

    static Properties buildProducerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
        Properties p = buildCommonConfigPlain(plainConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    static Properties buildProducerConfigScram(String kafkaBootstrap, Map<String, String> scramConfig) {
        Properties p = buildCommonConfigScram(scramConfig);
        setCommonProducerProperties(kafkaBootstrap, p);
        return p;
    }

    static Properties buildConsumerConfigPlain(String kafkaBootstrap, Map<String, String> plainConfig) {
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
                "application/x-www-form-urlencoded",
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
                "application/x-www-form-urlencoded",
                body,
                JsonNode.class);

        JsonNode token = result.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Invalid response from authorization server: no access_token");
        }
        return token.asText();
    }

    public static List<String> getKafkaLogsForError(String errString) {
        try {
            boolean inmatch = false;
            ArrayList result = new ArrayList();
            Pattern pat = Pattern.compile("\\[\\d\\d\\d\\d-\\d\\d-\\d\\d .*");

            Process p = Runtime.getRuntime().exec(new String[] {"docker", "logs", "kafka"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // is new logging entry?
                    if (pat.matcher(line).matches()) {
                        // contains the err string?
                        inmatch = line.contains(errString);
                        if (inmatch) {
                            result.add(line);
                        }
                    } else if (inmatch) {
                        result.add(line);
                    } else {
                        inmatch = false;
                    }
                }
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get 'kafka' log", e);
        }
    }
}
