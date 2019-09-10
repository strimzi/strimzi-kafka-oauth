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

        Config config = new Config();


        // TODO: Set KEYCLOAK_IP to be able to connect to Keycloak
        //  Use 'keycloak.ip' system property or KEYCLOAK_IP env property
        final String KEYCLOAK_IP = config.getValue("keycloak.ip", "KEYCLOAK IP");
        final String REALM = config.getValue("realm", "demo");

        final String TOKEN_ENDPOINT_URI = "http://" + KEYCLOAK_IP+ ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

        // TODO: You can set ACCESS_TOKEN or REFRESH_TOKEN to override default authentication with clientId and secret
        //  See README.md for more info.
        final String ACCESS_TOKEN = config.getValue("access.token", null);
        final String REFRESH_TOKEN = config.getValue("refresh.token", null);


        // By default, authenticate with client id, and secret to obtain access token from Keycloak server
        if (ACCESS_TOKEN == null && REFRESH_TOKEN == null) {
            System.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
            System.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            System.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }
        // If ACCESS_TOKEN is specified, pass it directly to Kafka Broker where it will be validated
        else if (ACCESS_TOKEN != null) {
            System.setProperty(ClientConfig.OAUTH_ACCESS_TOKEN, ACCESS_TOKEN);
        }
        // If REFRESH_TOKEN is specified, use it to obtain access token from Keycloak server
        else if (REFRESH_TOKEN != null) {
            System.setProperty(ClientConfig.OAUTH_REFRESH_TOKEN, REFRESH_TOKEN);
            System.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
            System.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            System.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }

        // Use 'preferred_username' rather than 'sub' for principal name
        System.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties props = configure();
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

    private static Properties configure() {

        ConfigProperties p = new ConfigProperties();

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        return p.toProperties();
    }
}