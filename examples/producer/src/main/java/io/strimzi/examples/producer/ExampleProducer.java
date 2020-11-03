/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.examples.producer;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class ExampleProducer {

    public static void main(String[] args) {

        String topic = "a_Topic1";

        Properties defaults = new Properties();
        Config external = new Config();

        //  Set KEYCLOAK_HOST to connect to Keycloak host other than 'keycloak'
        //  Use 'keycloak.host' system property or KEYCLOAK_HOST env variable

        final String keycloakHost = external.getValue("keycloak.host", "keycloak");
        final String realm = external.getValue("realm", "kafka-authz");
        final String tokenEndpointUri = "http://" + keycloakHost + ":8080/auth/realms/" + realm + "/protocol/openid-connect/token";

        //  You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property,
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable

        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);

        //  By defaut this client uses preconfigured clientId and secret to authenticate.
        //  You can set OAUTH_ACCESS_TOKEN or OAUTH_REFRESH_TOKEN to override default authentication.
        //
        //  If access token is configured, it is passed directly to Kafka broker
        //  If refresh token is configured, it is used in conjunction with clientId and secret
        //
        //  See examples README.md for more info.

        final String accessToken = external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN, null);

        if (accessToken == null) {
            defaults.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            defaults.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }

        // Use 'preferred_username' rather than 'sub' for principal name
        if (isAccessTokenJwt(external)) {
            defaults.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");
        }

        // Resolve external configurations falling back to provided defaults
        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties props = buildProducerConfig();
        Producer<String, String> producer = new KafkaProducer<>(props);

        for (int i = 0; ; i++) {
            try {

                producer.send(new ProducerRecord<>(topic, "Message " + i))
                        .get();

                System.out.println("Produced Message " + i);

            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while sending!");

            } catch (ExecutionException e) {
                if (e.getCause() instanceof AuthenticationException
                        || e.getCause() instanceof AuthorizationException) {
                    producer.close();
                    producer = new KafkaProducer<>(props);
                } else {
                    throw new RuntimeException("Failed to send message: " + i, e);
                }
            }

            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while sleeping!");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isAccessTokenJwt(Config config) {
        String legacy = config.getValue(Config.OAUTH_TOKENS_NOT_JWT);
        if (legacy != null) {
            System.out.println("[WARN] OAUTH_TOKENS_NOT_JWT is deprecated. Use OAUTH_ACCESS_TOKEN_IS_JWT (with reverse meaning) instead.");
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(Config.OAUTH_ACCESS_TOKEN_IS_JWT, true);
    }

    /**
     * Build KafkaProducer properties. The specified values are defaults that can be overridden
     * through runtime system properties or env variables.
     *
     * @return Configuration properties
     */
    private static Properties buildProducerConfig() {

        Properties p = new Properties();

        String username = "myclient";
        String password = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ2blhyakpIelBaQU04cVhoTXF4Rm4tcGRoSUptSFZVTFd2NjlKQW52M1Q0In0.eyJleHAiOjE2MDUyODUxNzgsImlhdCI6MTYwNDQyMTE3OCwianRpIjoiOWI5MjdlYzgtMDBiMi00YmVkLWI5OGMtNDYwYTc0Y2I0OGE5IiwiaXNzIjoiaHR0cDovL2tleWNsb2FrOjgwODAvYXV0aC9yZWFsbXMva2Fma2EtYXV0aHoiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiZTU3MTY5NjUtNDljYi00NjhjLWJhYmQtOWI0ODcwYWY1NjVmIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoibXljbGllbnQiLCJzZXNzaW9uX3N0YXRlIjoiZThmYmZmN2ItYTUwMy00ZDRkLWIwOTktNmVhYzU0ZTdmZTlmIiwiYWNyIjoiMSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SWQiOiJteWNsaWVudCIsImNsaWVudEhvc3QiOiIxNzIuMTguMC4yIiwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LW15Y2xpZW50IiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xOC4wLjIifQ.UaCVWWRAe9r0LNJfyW3M5C95fs_hlJ2CmgbbeKBRw5vnTPIWz294JJCQjeuXlABUkn2JraoETN6226rBFFaudZFNzmx2BAYziTvvo18lV9oKFf2ZPVKWDaiM0sih1sDBlamfFTNtOP2CPMNubqSKCMP-_dH5ffLpE9d38atQ8bCTKF9oSQZzRELDwftg5jnQssLaB9hgiVcbslH1eEm2uTY9ps2uBNE7Lq8_NOERSaqIy09haYqeRAZwtWvzs7L6pmiWAThlhdYICMUSYoe-7cOkLEZdRTyLdpIuso_tB1qDt8cem7vev8y_zSVi7DFqFtQv2R4VSKVAx3aFdNbUXQ";

        //p.setProperty("security.protocol", "SASL_PLAINTEXT");
        //p.setProperty("sasl.mechanism", "OAUTHBEARER");
        //p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        //p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "PLAIN");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + username + "\" password=\"" + password + "\" ;");
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        // Adjust re-authentication options
        // See: strimzi-kafka-oauth/README.md
        p.setProperty("sasl.login.refresh.buffer.seconds", "30");
        p.setProperty("sasl.login.refresh.min.period.seconds", "30");
        p.setProperty("sasl.login.refresh.window.factor", "0.8");
        p.setProperty("sasl.login.refresh.window.jitter", "0.01");

        return ConfigProperties.resolve(p);
    }
}