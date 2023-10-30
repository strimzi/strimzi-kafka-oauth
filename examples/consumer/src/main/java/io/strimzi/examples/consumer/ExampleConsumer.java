/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.examples.consumer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

/**
 * An example consumer implementation
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
public class ExampleConsumer {

    /**
     * A main method
     *
     * @param args No arguments expected
     */
    public static void main(String[] args) {

        String topic = "a_Topic1";

        Properties defaults = new Properties();
        Config external = new Config();

        //  Set KEYCLOAK_HOST to connect to Keycloak host other than 'keycloak'
        //  Use 'keycloak.host' system property or KEYCLOAK_HOST env variable

        final String keycloakHost = external.getValue("keycloak.host", "keycloak");
        final String realm = external.getValue("realm", "demo");
        final String tokenEndpointUri = "http://" + keycloakHost + ":8080/auth/realms/" + realm + "/protocol/openid-connect/token";

        //  You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property,
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable

        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);

        //  By default, this client uses preconfigured clientId and secret to authenticate.
        //  You can set OAUTH_ACCESS_TOKEN(_LOCATION) or OAUTH_REFRESH_TOKEN(_LOCATION)
        //  or OAUTH_CLIENT_ASSERTION(_LOCATION) to override default authentication behavior.
        //
        //  If access token is configured, it is passed directly to Kafka broker
        //  If refresh token is configured, it is used in conjunction with clientId and secret
        //
        //  See examples README.md for more info.

        final String accessToken = external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN, null);

        if (accessToken == null) {
            defaults.setProperty(Config.OAUTH_CLIENT_ID, "kafka-consumer-client");

            // use a secret for client_credentials authentication
            defaults.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-consumer-client-secret");

            // use private_key_jwt for client_credentials authentication
            //defaults.setProperty(ClientConfig.OAUTH_CLIENT_ASSERTION, "jwt-signed-by-trusted-key");
        }

        // Use 'preferred_username' rather than 'sub' for principal name
        if (isAccessTokenJwt(external)) {
            defaults.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");
        }

        // Resolve external configurations falling back to provided defaults
        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties props = buildConsumerConfig();
        Consumer<String, String> consumer = new KafkaConsumer<>(props);

        for (int i = 0; ; i++) {
            try {
                consumer.subscribe(Arrays.asList(topic));

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.println("Consumed message - " + i + ": " + record.value());
                    }
                }
            } catch (InterruptException e) {
                throw new RuntimeException("Interrupted while consuming message - " + i + "!");

            } catch (AuthenticationException | AuthorizationException e) {
                consumer.close();
                consumer = new KafkaConsumer<>(props);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isAccessTokenJwt(Config config) {
        String legacy = config.getValue(Config.OAUTH_TOKENS_NOT_JWT);
        if (legacy != null) {
            System.out.println("[WARN] Config option 'oauth.tokens.not.jwt' is deprecated. Use 'oauth.access.token.is.jwt' (with reverse meaning) instead.");
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(Config.OAUTH_ACCESS_TOKEN_IS_JWT, true);
    }

    /**
     * Build KafkaConsumer properties. The specified values are defaults that can be overridden
     * through runtime system properties or env variables.
     *
     * @return Configuration properties
     */
    private static Properties buildConsumerConfig() {

        Properties p = new Properties();

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "a_consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        return ConfigProperties.resolve(p);
    }
}
