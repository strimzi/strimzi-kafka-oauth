/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.keycloak.authz;

import io.strimzi.oauth.testsuite.common.ContainerLogLineReader;
import io.strimzi.oauth.testsuite.common.TestTags;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for KeycloakAuthorizer singleton behavior
 */
@OAuthEnvironment(authServer = AuthServer.KEYCLOAK, kafka = @KafkaConfig(realm = "kafka-authz",
    setupAcls = true,
    oauthProperties = {
        "oauth.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
        "oauth.client.id=kafka",
        "oauth.client.secret=kafka-secret",
        "oauth.groups.claim=$.realm_access.roles",
        "oauth.fallback.username.claim=username",
        "unsecuredLoginStringClaim_sub=admin"
    },
    kafkaProperties = {
        "authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer",
        "strimzi.authorization.token.endpoint.uri=http://keycloak:8080/realms/kafka-authz/protocol/openid-connect/token",
        "strimzi.authorization.client.id=kafka",
        "strimzi.authorization.client.secret=kafka-secret",
        "strimzi.authorization.kafka.cluster.name=my-cluster",
        "strimzi.authorization.delegate.to.kafka.acl=true",
        "strimzi.authorization.read.timeout.seconds=45",
        "strimzi.authorization.grants.refresh.pool.size=4",
        "strimzi.authorization.grants.refresh.period.seconds=10",
        "strimzi.authorization.http.retries=1",
        "strimzi.authorization.reuse.grants=true",
        "strimzi.authorization.enable.metrics=true",
        "super.users=User:admin;User:service-account-kafka"
    }))
public class SingletonIT {

    OAuthEnvironmentExtension env;

    /**
     * Ensure that multiple instantiated KeycloakAuthorizers share a single instance of KeycloakRBACAuthorizer
     */
    @Test
    @DisplayName("Test KeycloakAuthorizer singleton behavior")
    @Tag(TestTags.SINGLETON)
    @Tag(TestTags.AUTHORIZATION)
    public void testKeycloakAuthorizerSingleton() throws Exception {
        // In KRaft mode, there are 3 KeycloakAuthorizer instances
        int keycloakAuthorizersCount = 2;

        ContainerLogLineReader logReader = new ContainerLogLineReader(env.getKafka());

        List<String> lines = logReader.readNext();
        List<String> keycloakAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakAuthorizer@"))
                .collect(Collectors.toList());
        List<String> keycloakRBACAuthorizerLines = lines.stream()
                .filter(line -> line.contains("Configured KeycloakRBACAuthorizer@"))
                .collect(Collectors.toList());

        assertEquals(keycloakAuthorizersCount, keycloakAuthorizerLines.size(), "Configured KeycloakAuthorizer");
        assertEquals(1, keycloakRBACAuthorizerLines.size(), "Configured KeycloakRBACAuthorizer");
    }
}
