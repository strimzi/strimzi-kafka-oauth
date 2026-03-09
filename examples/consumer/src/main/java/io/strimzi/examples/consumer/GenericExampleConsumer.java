/*
 * Copyright 2017-2026, Strimzi authors.
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
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

/**
 * An example general use consumer implementation
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
public class GenericExampleConsumer {

    /**
     * This class should only be used via static {@link main(String[])} method  
     */
    private GenericExampleConsumer() {
    }

    /**
     * A main method
     *
     * @param args No arguments expected
     */
    public static void main(String[] args) {

        Properties defaults = new Properties();
        Config external = new Config();
        
        final String kafkaBootstrap = external.getValue("kafka.bootstrap");
        final String groupId = external.getValue("group.id");
        final String topic = external.getValue("topic");
        final String tokenEndpoint = external.getValue("token.endpoint");
        final String clientId = external.getValue("client.id");
        final String clientSecret = external.getValue("client.secret");

        final boolean isJwt = Boolean.valueOf(external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN_IS_JWT, "true"));
        final String usernameClaim = external.getValue(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        //  By default, this client uses preconfigured clientId and secret to authenticate.
        //  You can set OAUTH_ACCESS_TOKEN(_LOCATION) or OAUTH_REFRESH_TOKEN(_LOCATION)
        //  or OAUTH_CLIENT_ASSERTION(_LOCATION) to override default authentication behavior.
        //
        //  If access token is configured, it is passed directly to Kafka broker
        //  If refresh token is configured, it is used in conjunction with clientId and secret
        //
        //  See examples README.md for more info.

        final String accessToken = external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN);

        if (accessToken == null) {
            defaults.setProperty(Config.OAUTH_CLIENT_ID, clientId);

            // use a secret for client_credentials authentication
            defaults.setProperty(Config.OAUTH_CLIENT_SECRET, clientSecret);

            // use private_key_jwt for client_credentials authentication
            //defaults.setProperty(ClientConfig.OAUTH_CLIENT_ASSERTION, "jwt-signed-by-trusted-key");
        }

        //  You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property,
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable

        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpoint);


        // Use 'preferred_username' rather than 'sub' for principal name
        if (isJwt) {
            defaults.setProperty(Config.OAUTH_USERNAME_CLAIM, usernameClaim);
        }

        // Resolve external configurations falling back to provided defaults
        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties props = buildConsumerConfig(kafkaBootstrap, groupId);
        Consumer<String, String> consumer = new KafkaConsumer<>(props);

        int i = 1;
        try {
            while (true) {
                try {
                    // AuthenticationException or AuthorizationException during consuming messages
                    // will result in consumer set to null
                    while (consumer == null) {
                        try {
                            consumer = new KafkaConsumer<>(props);    
                        } catch (KafkaException e) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                throw new RuntimeException("Interrupted while creating a new KafkaConsumer - " + ie + "!");
                            }
                        }
                    }

                    consumer.subscribe(Arrays.asList(topic));

                    while (true) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                        for (ConsumerRecord<String, String> record : records) {
                            System.out.println("Consumed message - " + i++ + ": " + record.value());
                        }
                    }
                } catch (InterruptException e) {
                    throw new RuntimeException("Interrupted while consuming message - " + (i - 1) + "!");

                } catch (AuthenticationException | AuthorizationException e) {
                    
                    // Uncommenting the below code will cause the consumer to be recreated 
                    // thereby automatically fixing internal inconsistent timers state

                    //try {
                    //    consumer.close();
                    //} catch (Exception ex) {
                    //    System.out.println("Exception while closing consumer - " + ex);
                    //}
                    //consumer = null;
                }
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    /**
     * Build KafkaConsumer properties. The specified values are defaults that can be overridden
     * through runtime system properties or env variables.
     *
     * @return Configuration properties
     */
    private static Properties buildConsumerConfig(String kafkaBootstrap, String groupId) {

        Properties p = new Properties();

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        return ConfigProperties.resolve(p);
    }
}
