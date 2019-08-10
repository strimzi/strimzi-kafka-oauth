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

        Config config = new Config();


        // TODO: To be able to connect to Keycloak properly set KEYCLOAK_IP
        //  You can set system property 'keycloak.ip' or ENV property KEYCLOAK_IP, and similarly for 'realm'
        final String KEYCLOAK_IP = config.getValue("keycloak.ip", "KEYCLOAK IP");
        final String REALM = config.getValue("realm", "demo");

        final String TOKEN_ENDPOINT_URI = "http://" + KEYCLOAK_IP+ ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

        // TODO: You can set system property 'access.token' or ENV property ACCESS_TOKEN
        //  and analogously for 'refresh.token' / REFRESH_TOKEN
        //  See README.md for more info.
        final String ACCESS_TOKEN = config.getValue("access.token", null);
        final String REFRESH_TOKEN = config.getValue("refresh.token", null);


        // By default, authenticate with client id, and secret to obtain access token by contacting Keycloak server
        if (ACCESS_TOKEN == null && REFRESH_TOKEN == null) {
            System.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
            System.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            System.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }
        // If ACCESS_TOKEN is specified, pass it directly to Kafka Broker where it will be validated
        else if (ACCESS_TOKEN != null) {
            System.setProperty(ClientConfig.OAUTH_ACCESS_TOKEN, ACCESS_TOKEN);
        }
        // If REFRESH_TOKEN is specified, use it to obtain access token by contacting Keycloak server
        else if (REFRESH_TOKEN != null) {
            System.setProperty(ClientConfig.OAUTH_REFRESH_TOKEN, REFRESH_TOKEN);
            System.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
            System.setProperty(Config.OAUTH_CLIENT_ID, "kafka-producer-client");
            System.setProperty(Config.OAUTH_CLIENT_SECRET, "kafka-producer-client-secret");
        }

        // Use 'preferred_username' rather than 'sub' for principal name
        System.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");

        Properties props = configure();
        Consumer<String, String> consumer = new KafkaConsumer<>(props);

        consumer.subscribe(Arrays.asList(topic));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("Consumed message: " + record.value());
            }
        }
    }


    private static Properties configure() {

        ConfigProperties p = new ConfigProperties();

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

        return p.toProperties();
    }
}
