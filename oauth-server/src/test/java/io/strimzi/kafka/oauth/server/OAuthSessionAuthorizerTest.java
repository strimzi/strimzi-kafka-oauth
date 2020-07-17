/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.TokenInfo;
import kafka.network.RequestChannel;
import kafka.security.auth.Acl;
import kafka.security.auth.Authorizer;
import kafka.security.auth.Operation;
import kafka.security.auth.Resource;
import kafka.security.auth.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.junit.Assert;
import org.junit.Test;
import scala.collection.immutable.Map;
import scala.collection.immutable.Set;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

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

    private void testOAuthUserWithExpiredTokenWithoutDelegate(Authorizer authorizer) throws Exception {

        // Make it so that the token is expired
        TokenInfo tokenInfo = new TokenInfo("accesstoken234", null, "User:bob",
                System.currentTimeMillis() - 200000,
                System.currentTimeMillis() - 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "bob", token);
        RequestChannel.Session session = new RequestChannel.Session(principal, InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should return false
        boolean granted = authorizer.authorize(session, op, resource);
        Assert.assertFalse("Should be denied", granted);
    }

    private void testOAuthUserWithoutDelegate(Authorizer authorizer) throws Exception {

        // Prepare condition after mock OAuth athentication with valid token
        TokenInfo tokenInfo = new TokenInfo("accesstoken123", null, "User:bob",
                System.currentTimeMillis() - 100000,
                System.currentTimeMillis() + 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "bob", token);
        RequestChannel.Session session = new RequestChannel.Session(principal, InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should return true
        boolean granted = authorizer.authorize(session, op, resource);
        Assert.assertTrue("Should be granted", granted);
    }

    private void testNonOAuthUserWithoutDelegate(Authorizer authorizer) throws Exception {

        // Prepare arguments for authorize() call
        RequestChannel.Session session = new RequestChannel.Session(new KafkaPrincipal("User", "CN=admin"), InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should return true
        boolean granted = authorizer.authorize(session, op, resource);
        Assert.assertTrue("Should be granted", granted);
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

    private void testNonOAuthUserWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Prepare arguments for authorize() call
        RequestChannel.Session session = new RequestChannel.Session(new KafkaPrincipal("User", "CN=admin"), InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should be delegated because principal is not instanceof OAuthKafkaPrincipal
        boolean granted = authorizer.authorize(session, op, resource);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should contain two entries", 2, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("authorize() call should be delegated", MockAuthorizerType.AUTHORIZE, lastEntry.type);
        Assert.assertEquals("Call args should be equal - session", session, lastEntry.session);
        Assert.assertEquals("Call args should be equal - operation", op, lastEntry.operation);
        Assert.assertEquals("Call args should be equal - resource", resource, lastEntry.resource);
        Assert.assertTrue("Should be granted", granted);
    }

    private void testOAuthUserWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Prepare condition after mock OAuth athentication with valid token
        TokenInfo tokenInfo = new TokenInfo("accesstoken123", null, "User:bob",
                System.currentTimeMillis() - 100000,
                System.currentTimeMillis() + 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "bob", token);

        RequestChannel.Session session = new RequestChannel.Session(principal, InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should be delegated because the OAuthKafkaPrincipa contains a valid token
        boolean granted = authorizer.authorize(session, op, resource);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should contain three entries", 3, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("authorize() call should be delegated", MockAuthorizerType.AUTHORIZE, lastEntry.type);
        Assert.assertEquals("Call args should be equal - session", session, lastEntry.session);
        Assert.assertEquals("Call args should be equal - session.token", token, ((OAuthKafkaPrincipal) lastEntry.session.principal()).getJwt());
        Assert.assertEquals("Call args should be equal - operation", op, lastEntry.operation);
        Assert.assertEquals("Call args should be equal - resource", resource, lastEntry.resource);
        Assert.assertTrue("Should be granted", granted);
    }

    public void testOAuthUserWithExpiredTokenWithDelegate(Authorizer authorizer, MockAuthorizer delegateAuthorizer) throws Exception {

        // Make it so that the token is expired
        TokenInfo tokenInfo = new TokenInfo("accesstoken234", null, "User:bob",
                System.currentTimeMillis() - 200000,
                System.currentTimeMillis() - 100000);
        BearerTokenWithPayload token = new JaasServerOauthValidatorCallbackHandler.BearerTokenWithPayloadImpl(tokenInfo);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal("User", "bob", token);
        RequestChannel.Session session = new RequestChannel.Session(principal, InetAddress.getLocalHost());
        Operation op = Operation.fromString("READ");
        Resource resource = new Resource(ResourceType.fromString("TOPIC"), "my-topic");

        // authorize() call should not be delegated because the OAuthKafkaPrincipa contains an expired token
        boolean granted = authorizer.authorize(session, op, resource);

        MockAuthorizerLog lastEntry = delegateAuthorizer.invocationLog.getLast();
        Assert.assertEquals("Invocation log should still contain three entries", 3, delegateAuthorizer.invocationLog.size());
        Assert.assertEquals("Call args should be equal - session", session, lastEntry.session);
        Assert.assertEquals("Principal type should be OAuthKafkaPrincipal", OAuthKafkaPrincipal.class, lastEntry.session.principal().getClass());
        Assert.assertNotEquals("Call args should be different - session.token", token, ((OAuthKafkaPrincipal) lastEntry.session.principal()).getJwt());
        Assert.assertEquals("Call args should be equal - operation", op, lastEntry.operation);
        Assert.assertEquals("Call args should be equals - resource", resource, lastEntry.resource);
        Assert.assertFalse("Should be denied", granted);
    }

    public static class MockAuthorizer implements Authorizer {

        LinkedList<MockAuthorizerLog> invocationLog = new LinkedList<>();

        public MockAuthorizer() {
            mockAuthorizerTL.set(this);
        }

        @Override
        public boolean authorize(RequestChannel.Session session, Operation operation, Resource resource) {
            invocationLog.add(new MockAuthorizerLog(session, operation, resource));
            return true;
        }

        @Override
        public void addAcls(Set<Acl> acls, Resource resource) {
        }

        @Override
        public boolean removeAcls(Set<Acl> acls, Resource resource) {
            return false;
        }

        @Override
        public boolean removeAcls(Resource resource) {
            return false;
        }

        @Override
        public Set<Acl> getAcls(Resource resource) {
            return null;
        }

        @Override
        public Map<Resource, Set<Acl>> getAcls(KafkaPrincipal principal) {
            return null;
        }

        @Override
        public Map<Resource, Set<Acl>> getAcls() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public void configure(java.util.Map<String, ?> configs) {
            invocationLog.add(new MockAuthorizerLog(configs));
        }
    }

    static class MockAuthorizerLog {

        private final MockAuthorizerType type;
        private RequestChannel.Session session;
        private Operation operation;
        private Resource resource;
        private java.util.Map<String, ?> config;

        long entryTime = System.currentTimeMillis();

        MockAuthorizerLog(RequestChannel.Session session, Operation operation, Resource resource) {
            this.type = MockAuthorizerType.AUTHORIZE;
            this.session = session;
            this.operation = operation;
            this.resource = resource;
        }

        MockAuthorizerLog(java.util.Map<String, ?> config) {
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
