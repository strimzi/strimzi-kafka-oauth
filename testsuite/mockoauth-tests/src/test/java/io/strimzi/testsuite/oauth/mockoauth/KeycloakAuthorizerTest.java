/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipal;
import io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthUser;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeycloakAuthorizerTest {

    String clientCli = "kafka-cli";

    String userAlice = "alice";
    String userAlicePass = "alice-password";

    String truststorePath = "../docker/target/kafka/certs/ca-truststore.p12";
    String truststorePass = "changeit";
    String tokenEndpoint = "https://mockoauth:8090/failing_token";

    public void doHttpRetriesTest() throws IOException {

        // create a client for resource server
        String clientSrv = "kafka";
        String clientSrvSecret = "kafka-secret";
        createOAuthClient(clientSrv, clientSrvSecret);

        // create a client for user's client agent
        createOAuthClient(clientCli, "");

        // create a user alice
        createOAuthUser(userAlice, userAlicePass);

        changeAuthServerMode("token", "MODE_200");
        changeAuthServerMode("failing_token", "MODE_400");

        HashMap<String, String> props = new HashMap<>();
        props.put("strimzi.authorization.ssl.truststore.location", truststorePath);
        props.put("strimzi.authorization.ssl.truststore.password", truststorePass);
        props.put("strimzi.authorization.ssl.truststore.type", "pkcs12");

        props.put("strimzi.authorization.enable.metrics", "true");
        props.put("strimzi.authorization.token.endpoint.uri", "https://mockoauth:8090/failing_grants");
        props.put("strimzi.authorization.client.id", clientSrv);
        props.put("strimzi.authorization.client.secret", clientSrvSecret);
        props.put("strimzi.authorization.kafka.cluster.name", "my-cluster");
        props.put("strimzi.authorization.delegate.to.kafka.acl", "false");
        props.put("strimzi.authorization.read.timeout.seconds", "45");
        props.put("strimzi.authorization.connect.timeout.seconds", "10");
        props.put("strimzi.authorization.grants.refresh.pool.size", "2");
        props.put("strimzi.authorization.grants.refresh.period.seconds", "60");
        props.put("strimzi.authorization.http.retries", "1");
        props.put("super.users", "User:admin;User:service-account-kafka");
        props.put("principal.builder.class", "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");

        KeycloakRBACAuthorizer authorizer = new KeycloakRBACAuthorizer();
        authorizer.configure(props);


        SecurityProtocol protocol = SecurityProtocol.SASL_PLAINTEXT;

        try {
            login(tokenEndpoint, userAlice, userAlicePass, 0);

            Assert.fail("Should have failed while logging in with password");

        } catch (Exception expected) {
            login(tokenEndpoint, userAlice, userAlicePass, 0);
        }

        // Now try again
        TokenInfo tokenInfo = login(tokenEndpoint, userAlice, userAlicePass, 1);

        KafkaPrincipal principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, "alice", new Common.MockBearerTokenWithPayload(tokenInfo));


        AuthorizableRequestContext ctx = mock(AuthorizableRequestContext.class);
        when(ctx.listenerName()).thenReturn("JWT");
        when(ctx.securityProtocol()).thenReturn(protocol);
        when(ctx.principal()).thenReturn(principal);
        when(ctx.clientId()).thenReturn(clientCli);


        List<Action> actions = new ArrayList<>();
        actions.add(new Action(
                AclOperation.CREATE,
                new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                1, true, true));


        List<AuthorizationResult> result = authorizer.authorize(ctx, actions);
        Assert.assertNotNull("Authorizer has to return non-null", result);
        Assert.assertTrue("Authorizer has to return as many results as it received inputs", result.size() == actions.size());
        Assert.assertEquals("Authorizer should return ALLOWED", AuthorizationResult.ALLOWED, result.get(0));
    }

    @NotNull
    private TokenInfo login(String tokenEndpoint, String user, String userPass, int retries) throws IOException {
        return OAuthAuthenticator.loginWithPassword(
                URI.create(tokenEndpoint),
                SSLUtil.createSSLFactory(truststorePath, null, truststorePass, null, null),
                null,
                user,
                userPass,
                clientCli,
                null,
                true,
                new PrincipalExtractor(),
                "all",
                null,
                60,
                60,
                retries,
                0);
    }
}
