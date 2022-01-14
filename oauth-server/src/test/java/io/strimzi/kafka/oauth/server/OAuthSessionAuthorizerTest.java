/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.TokenInfo;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.Authorizer;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletionStage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OAuthSessionAuthorizerTest {

    static ThreadLocal<MockAuthorizer> mockAuthorizerTL = new ThreadLocal<MockAuthorizer>();

    @Test
    public void testSessionAuthorizer() throws Exception {

        // Prepare configuration
        Properties props = new Properties();
        props.setProperty("authorizer.class.name", OAuthSessionAuthorizer.class.getTypeName());
        props.setProperty("principal.builder.class", OAuthKafkaPrincipalBuilder.class.getTypeName());
        props.setProperty("strimzi.authorizer.delegate.class.name", MockAuthorizer.class.getTypeName());
        java.util.Map<String, String> config = new HashMap(props);

        Authorizer authorizer = new OAuthSessionAuthorizer();
        authorizer.configure(config);

        MockAuthorizer delegateAuthorizer = mockAuthorizerTL.get();
        Assert.assertTrue("MockAuthorizer zero args constructor should be invoked", delegateAuthorizer != null);
        Assert.assertEquals("Invocation log contains one entry", 1, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("Configuration should be passed to delegate", config, delegateAuthorizer.invocationLog.getLast().config);

        testNonOAuthUserWithDelegate(authorizer, delegateAuthorizer);

        testOAuthUserWithDelegate(authorizer, delegateAuthorizer);

        testOAuthUserWithExpiredTokenWithDelegate(authorizer, delegateAuthorizer);

        // prepare the authorizer without the delegate, testing various misconfigurations in the process
        authorizer = testConfiguringAuthorizerWithoutDelegate(authorizer, config);

        testNonOAuthUserWithoutDelegate(authorizer);

        testOAuthUserWithoutDelegate(authorizer);

        testOAuthUserWithExpiredTokenWithoutDelegate(authorizer);
    }

    private void testNonOAuthUserWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Prepare arguments for authorize() call
        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        AuthorizableRequestContext ctx = requestContext(new KafkaPrincipal("User", "CN=admin"));

        // authorize() call should be delegated because principal is not instanceof OAuthKafkaPrincipal
        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should contain two entries", 2, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("authorize() call should be delegated", MockAuthorizerType.AUTHORIZE, lastEntry.type);
        Assert.assertEquals("Call args should be equal - context", ctx, lastEntry.context);
        Assert.assertEquals("Call args should be equal - actions", actions, lastEntry.actions);
        Assert.assertEquals("Should be allowed", AuthorizationResult.ALLOWED, results.get(0));
    }

    private void testOAuthUserWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Prepare condition after mock OAuth athentication with valid token
        TokenInfo tokenInfo = new TokenInfo("accesstoken123", null, "User:bob", new HashSet(Arrays.asList("group1", "group2")),
                System.currentTimeMillis() - 100000,
                System.currentTimeMillis() + 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);

        AuthorizableRequestContext ctx = requestContext(new OAuthKafkaPrincipal("User", "bob", token));

        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        // authorize() call should be delegated because the OAuthKafkaPrincipa contains a valid token
        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should contain three entries", 3, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("authorize() call should be delegated", MockAuthorizerType.AUTHORIZE, lastEntry.type);
        Assert.assertEquals("Call args should be equal - context", ctx, lastEntry.context);
        Assert.assertEquals("Call args should be equal - session.token", token, ((OAuthKafkaPrincipal) lastEntry.context.principal()).getJwt());
        Assert.assertEquals("Call args should be equal - actions", actions, lastEntry.actions);
        Assert.assertEquals("Should be allowed", AuthorizationResult.ALLOWED, results.get(0));
    }

    public void testOAuthUserWithExpiredTokenWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Make it so that the token is expired
        TokenInfo tokenInfo = new TokenInfo("accesstoken234", null, "User:bob", null,
                System.currentTimeMillis() - 200000,
                System.currentTimeMillis() - 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);

        AuthorizableRequestContext ctx = requestContext(new OAuthKafkaPrincipal("User", "bob", token));

        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        // authorize() call should not be delegated because the OAuthKafkaPrincipa contains an expired token
        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should still contain three entries", 3, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("Principal type should be OAuthKafkaPrincipal", OAuthKafkaPrincipal.class, lastEntry.context.principal().getClass());
        Assert.assertNotEquals("Call args should be different - context.token", token, ((OAuthKafkaPrincipal) lastEntry.context.principal()).getJwt());
        Assert.assertEquals("Should be denied", AuthorizationResult.DENIED, results.get(0));
    }

    private Authorizer testConfiguringAuthorizerWithoutDelegate(Authorizer authorizer, java.util.Map<String, String> config) {

        // Test authorizer without the delegate
        config.remove("strimzi.authorizer.delegate.class.name");
        Assert.assertEquals("Properties contain exactly 2 keys", 2, config.size());

        authorizer = new OAuthSessionAuthorizer();
        try {
            authorizer.configure(config);

            Assert.fail("Call to configure() should fail due to misconfiguration");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("'strimzi.authorizer.grant.when.no.delegate=true' has to be specified"));
        }

        // set the option to a bad value
        config.put("strimzi.authorizer.grant.when.no.delegate", "grant");

        // configure should fail
        try {
            authorizer.configure(config);

            Assert.fail("Call to configure() should fail due to misconfiguration");
        } catch (IllegalArgumentException ignored) {
        }


        // set the option to another bad value
        config.put("strimzi.authorizer.grant.when.no.delegate", "false");

        // configure should fail
        try {
            authorizer.configure(config);

            Assert.fail("Call to configure() should fail due to misconfiguration");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("'strimzi.authorizer.grant.when.no.delegate=true' has to be specified"));
        }

        // set the option to the only valid value
        config.put("strimzi.authorizer.grant.when.no.delegate", "true");

        // configure should now succeed
        authorizer.configure(config);
        return authorizer;
    }

    private void testNonOAuthUserWithoutDelegate(Authorizer authorizer) throws Exception {

        AuthorizableRequestContext ctx = requestContext(new KafkaPrincipal("User", "CN=admin"));

        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        // authorize() call should be delegated because the OAuthKafkaPrincipa contains a valid token
        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        Assert.assertEquals("Should be allowed", AuthorizationResult.ALLOWED, results.get(0));
    }

    private void testOAuthUserWithoutDelegate(Authorizer authorizer) throws Exception {

        // Prepare condition after mock OAuth athentication with valid token
        TokenInfo tokenInfo = new TokenInfo("accesstoken123", null, "User:bob", null,
                System.currentTimeMillis() - 100000,
                System.currentTimeMillis() + 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);

        AuthorizableRequestContext ctx = requestContext(new OAuthKafkaPrincipal("User", "bob", token));

        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        // authorize() call should not be delegated because the OAuthKafkaPrincipa contains an expired token
        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        Assert.assertEquals("Should be allowed", AuthorizationResult.ALLOWED, results.get(0));
    }

    private void testOAuthUserWithExpiredTokenWithoutDelegate(Authorizer authorizer) throws Exception {

        // Make it so that the token is expired
        TokenInfo tokenInfo = new TokenInfo("accesstoken234", null, "User:bob", null,
                System.currentTimeMillis() - 200000,
                System.currentTimeMillis() - 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "bob", token);

        List<Action> actions = Arrays.asList(
                new Action(AclOperation.READ,
                        new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                        0,
                        true,
                        true
                ));

        // authorize() call should return DENIED
        List<AuthorizationResult> results = authorizer.authorize(requestContext(principal), actions);
        Assert.assertEquals("Should be denied", AuthorizationResult.DENIED, results.get(0));
    }

    private AuthorizableRequestContext requestContext(KafkaPrincipal principal) throws UnknownHostException {
        AuthorizableRequestContext ctx = mock(AuthorizableRequestContext.class);
        when(ctx.principal()).thenReturn(principal);
        when(ctx.clientAddress()).thenReturn(InetAddress.getLocalHost());
        return ctx;
    }

    public static class MockAuthorizer implements Authorizer {

        LinkedList<MockAuthorizerLog> invocationLog = new LinkedList<>();

        public MockAuthorizer() {
            mockAuthorizerTL.set(this);
        }

        @Override
        public List<AuthorizationResult> authorize(AuthorizableRequestContext authorizableRequestContext, List<Action> list) {
            invocationLog.add(new MockAuthorizerLog(authorizableRequestContext, list));
            return Arrays.asList(AuthorizationResult.ALLOWED);
        }


        @Override
        public void close() {

        }

        @Override
        public void configure(Map<String, ?> configs) {
            invocationLog.add(new MockAuthorizerLog(configs));
        }

        @Override
        public Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo authorizerServerInfo) {
            return null;
        }

        @Override
        public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext authorizableRequestContext, List<AclBinding> list) {
            return null;
        }

        @Override
        public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext authorizableRequestContext, List<AclBindingFilter> list) {
            return null;
        }

        @Override
        public Iterable<AclBinding> acls(AclBindingFilter aclBindingFilter) {
            return null;
        }
    }

    static class MockAuthorizerLog {

        private final MockAuthorizerType type;
        private AuthorizableRequestContext context;
        private List<Action> actions;
        private Map<String, ?> config;

        long entryTime = System.currentTimeMillis();

        MockAuthorizerLog(AuthorizableRequestContext requestContext, java.util.List<Action> actions) {
            this.type = MockAuthorizerType.AUTHORIZE;
            this.context = requestContext;
            this.actions = actions;
        }

        MockAuthorizerLog(Map<String, ?> config) {
            this.type = MockAuthorizerType.CONFIGURE;
            this.config = config;
        }
    }

    enum MockAuthorizerType {
        CONFIGURE,
        AUTHORIZE,
        ADD_ACLS,
        REMOVE_ACLS,
        GET_ACLS,
        CLOSE
    }

}
