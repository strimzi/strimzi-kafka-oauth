/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.testsuite.oauth.common.LogLineReader;
import io.strimzi.testsuite.oauth.common.TestUtil;
import io.strimzi.testsuite.oauth.mockoauth.metrics.Metrics;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64encode;

public class Common {

    static final String WWW_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String LOG_PATH = "target/test.log";

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

    static Properties buildCommonConfigPlain(Map<String, String> plainConfig) {
        String configOptions = getJaasConfigOptionsString(plainConfig);

        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "PLAIN");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " + configOptions + " ;");
        return p;
    }

    /**
     * Get an access token from the token endpoint using client credentials by way of the <code>OAuthAuthenticator.loginWithClientSecret</code> method.
     *
     * @param tokenEndpoint The token endpoint
     * @param clientId The client ID
     * @param secret The client secret
     * @param truststorePath The path to the truststore for TLS connection
     * @param truststorePass The truststore password for TLS connection
     * @return The access token returned from the authorization server's token endpoint
     *
     * @throws IOException Exception while sending the request
     */
    static String loginWithClientSecret(String tokenEndpoint, String clientId, String secret, String truststorePath, String truststorePass) throws IOException {
        TokenInfo tokenInfo = OAuthAuthenticator.loginWithClientSecret(
                URI.create(tokenEndpoint),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
                null,
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
     * <p>
     * The Mock OAuth server takes the extraAttrs and writes them into the payload of the generated access token.
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
    static String loginWithClientSecretAndExtraAttrs(String tokenEndpoint, String clientId, String secret,
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
                null,
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

    static String loginWithUsernameForRefreshToken(String tokenEndpointUri, String username, String password, String clientId, String truststorePath, String truststorePass) throws IOException {

        JsonNode result = HttpUtil.post(URI.create(tokenEndpointUri),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
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

    /**
     * Get response from prometheus endpoint as a map of key:value pairs
     * We expect the response to be a 'well-formed' key=value document in the sense that each line contains a '=' sign
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
        if ("server".equals(resource)) {
            try {
                // This is to work around a race condition when switching server mode
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Interrupted!");
            }
        }
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

    public static void createOAuthUser(String username, String password, long expiresInSeconds) throws IOException {
        HttpUtil.post(URI.create("http://mockoauth:8091/admin/users"),
                null,
                "application/json",
                "{\"username\": \"" + username + "\", \"password\": \"" + password + "\", \"expires_in\": " + expiresInSeconds + "}", String.class);
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

    static void checkLog(LogLineReader logReader, String... args) throws IOException {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Args should be in pairs but there is an odd number of them.");
        }
        List<String> lines = logReader.readNext();

        for (int i = 0; i < args.length; i += 2) {
            Assert.assertEquals(args[i] + " =~ " + args[i + 1], 1, TestUtil.countLogForRegex(lines, args[i] + ":.*" + args[i + 1]));
        }
    }
}
