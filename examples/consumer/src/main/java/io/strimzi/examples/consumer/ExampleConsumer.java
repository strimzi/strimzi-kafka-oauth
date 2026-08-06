/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.examples.consumer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.Config;


/**
 * An example consumer implementation, with defaults to work with examples README.md
 * 
 * The following ENV vars / system properties can be used to configure it:
 * 
 * KEYCLOAK_HOST / keycloak.host
 * REALM / realm
 * KAFKA_BOOTSTRAP / kafka.bootstrap
 * GROUP_ID / group.id
 * TOPIC / topic
 * OAUTH_CLIENT_ID / oauth.client.id
 * OAUTH_CLIENT_SECRET / oauth.client.secret
 * OAUTH_ACCESS_TOKEN_IS_JWT / oauth.access.token.is.jwt
 * OAUTH_USERNAME_CLAIM / oauth.username.claim
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
public class ExampleConsumer {

    /**
     * This class should only be used via static {@link main(String[])} method  
     */
    private ExampleConsumer() {
    }

    /**
     * A main method
     *
     * @param args No arguments expected
     */
    public static void main(String[] args) {
        
        Config external = new Config();

        System.setProperty("kafka.bootstrap", "localhost:9092");
        System.setProperty("group.id", "a_consumer-group");
        System.setProperty("topic", "a_Topic1");
        System.setProperty("oauth.client.id", "kafka-consumer-client");
        System.setProperty("oauth.client.secret", "kafka-consumer-client-secret");
        System.setProperty("oauth.access.token.is.jwt", "true");
        System.setProperty("oauth.username.claim", "preferred_username");

        
        final String keycloakHost = external.getValue("keycloak.host", "keycloak");
        final String realm = external.getValue("realm", "demo");
        final String tokenEndpointUri = "http://" + keycloakHost + ":8080/realms/" + realm + "/protocol/openid-connect/token";

        System.setProperty("oauth.token.endpoint.uri", tokenEndpointUri);

        // Forward to GenericExampleConsumer.main()
        GenericExampleConsumer.main(args);
    }
}
