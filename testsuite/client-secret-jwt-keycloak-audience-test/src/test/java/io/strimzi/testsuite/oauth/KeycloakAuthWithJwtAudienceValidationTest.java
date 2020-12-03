/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Properties;
import java.util.concurrent.ExecutionException;


@RunWith(Arquillian.class)
public class KeycloakAuthWithJwtAudienceValidationTest {

    private static final String HOST = "keycloak";
    private static final String REALM = "kafka-authz";
    private static final String TOKEN_ENDPOINT_URI = "http://" + HOST + ":8080/auth/realms/" + REALM + "/protocol/openid-connect/token";

    private static final String TEAM_A_CLIENT = "team-a-client";
    private static final String TEAM_B_CLIENT = "team-b-client";


    @Test
    public void doTest() throws Exception {
        System.out.println("==== KeycloakAuthWithJwtAudienceValidationTest ====");

        Properties defaults = new Properties();
        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, TOKEN_ENDPOINT_URI);
        defaults.setProperty(ClientConfig.OAUTH_USERNAME_CLAIM, "preferred_username");

        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        Properties p = System.getProperties();
        for (Object key: p.keySet()) {
            System.out.println("" + key + "=" + p.get(key));
        }

        Properties producerProps = buildProducerConfig(TEAM_B_CLIENT, TEAM_B_CLIENT + "-secret");
        Producer<String, String> producer = new KafkaProducer<>(producerProps);
        RecordMetadata result = producer.send(new ProducerRecord<>("b_topic", "message")).get();

        Assert.assertTrue("Has offset", result.hasOffset());
        producer.close();

        producerProps = buildProducerConfig(TEAM_A_CLIENT, TEAM_A_CLIENT + "-secret");
        producer = new KafkaProducer<>(producerProps);
        try {
            producer.send(new ProducerRecord<>("whatever", "message")).get();
            Assert.fail();

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("instanceOf AuthenticationException", cause instanceof AuthenticationException);
            Assert.assertTrue("audience check failed", cause.toString().contains("audience"));
        }
    }

    static Properties buildProducerConfig(String clientId, String secret) {
        Properties p = new Properties();
        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                " oauth.client.id=\"" + clientId + "\" oauth.client.secret=\"" + secret + "\";");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        return p;
    }
}
