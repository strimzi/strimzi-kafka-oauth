/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.examples.consumer;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class ExampleConsumer {

    public static void main(String[] args) {

        String topic = "Topic1";

        Properties defaults = new Properties();
        Config external = new Config();

        //  Set KEYCLOAK_HOST to be able to connect to Keycloak
        //  Use 'keycloak.host' system property or KEYCLOAK_HOST env variable

        final String KEYCLOAK_HOST = external.getValue("keycloak.host", "keycloak");
        final String REALM = external.getValue("realm", "demo");
        final String TOKEN_ENDPOINT_URI = "http://" + KEYCLOAK_HOST + ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

        //  You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable
        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);


        //  By defaut this client uses preconfigured clientId and secret to authenticate.
        //  You can set OAUTH_ACCESS_TOKEN or OAUTH_REFRESH_TOKEN to override default authentication.
        //
        //  If access token is configured, it is passed directly to Kafka broker
        //  If refresh token is configured, it is used in conjunction with clientId and secret
        //
        //  See README.md for more info.

        final String ACCESS_TOKEN = external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN, null);

        if (ACCESS_TOKEN == null) {
            defaults.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            defaults.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }

        // Use 'preferred_username' rather than 'sub' for principal name
        defaults.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");

        // Resolve external configurations falling back to provided defaults
        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties props = buildConsumerConfig();
        Consumer<String, String> consumer = new KafkaConsumer<>(props);

        consumer.subscribe(Arrays.asList(topic));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("Consumed message: " + record.value());
            }
        }
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

        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        return ConfigProperties.resolve(p);
    }
}
