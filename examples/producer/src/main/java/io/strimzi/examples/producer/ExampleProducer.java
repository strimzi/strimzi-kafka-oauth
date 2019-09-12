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
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class ExampleProducer {

    public static void main(String[] args) {

        String topic = "Topic1";

        Properties defaults = new Properties();
        Config external = new Config();

        // TODO: Set KEYCLOAK_IP to be able to connect to Keycloak
        //  Use 'keycloak.ip' system property or KEYCLOAK_IP env variable

        final String KEYCLOAK_IP = external.getValue("keycloak.ip", "keycloak");
        final String REALM = external.getValue("realm", "demo");
        final String TOKEN_ENDPOINT_URI = "http://" + KEYCLOAK_IP+ ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

        // You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable
        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);


        // TODO: By defaut this client uses preconfigured clientId and secret to authenticate.
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
                throw new RuntimeException("Failed to send message: " + i, e);
            }


            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while sleeping!");
            }
        }
    }

    private static Properties buildProducerConfig() {

        Properties p = new Properties();

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        return ConfigProperties.resolve(p);
    }
}