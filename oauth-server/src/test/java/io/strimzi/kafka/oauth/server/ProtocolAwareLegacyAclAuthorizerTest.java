/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.Authorizer;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import io.strimzi.kafka.oauth.common.ConfigException;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtocolAwareLegacyAclAuthorizerTest {

    static ThreadLocal<MockAuthorizer> mockAuthorizerTL = new ThreadLocal<>();

    @Test
    public void testLegacyProtocolPrincipalIsNormalized() throws Exception {
        Authorizer authorizer = createAuthorizer();
        MockAuthorizer delegate = mockAuthorizerTL.get();

        KafkaPrincipal principal = new KafkaPrincipal("User", "wap-kafka:meeting#clid:my-client");
        AuthorizableRequestContext ctx = requestContext(principal, SecurityProtocol.SASL_SSL);
        List<Action> actions = Collections.singletonList(new Action(
                AclOperation.READ,
                new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                0,
                true,
                true
        ));

        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        Assert.assertEquals(AuthorizationResult.ALLOWED, results.get(0));
        Assert.assertEquals(2, delegate.invocationLog.size());
        Assert.assertEquals("wap-kafka:meeting", delegate.invocationLog.getLast().context.principal().getName());
    }

    @Test
    public void testNonLegacyProtocolPrincipalIsUnchanged() throws Exception {
        Authorizer authorizer = createAuthorizer();
        MockAuthorizer delegate = mockAuthorizerTL.get();

        KafkaPrincipal principal = new KafkaPrincipal("User", "wap-kafka:meeting#clid:my-client");
        AuthorizableRequestContext ctx = requestContext(principal, SecurityProtocol.PLAINTEXT);
        List<Action> actions = Collections.singletonList(new Action(
                AclOperation.READ,
                new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                0,
                true,
                true
        ));

        List<AuthorizationResult> results = authorizer.authorize(ctx, actions);

        Assert.assertEquals(AuthorizationResult.ALLOWED, results.get(0));
        Assert.assertEquals(2, delegate.invocationLog.size());
        Assert.assertEquals("wap-kafka:meeting#clid:my-client", delegate.invocationLog.getLast().context.principal().getName());
    }

    @Test(expected = ConfigException.class)
    public void testAclsManagementFailsWithoutDelegate() {
        Map<String, String> config = new HashMap<>();
        config.put("authorizer.class.name", ProtocolAwareLegacyAclAuthorizer.class.getTypeName());
        config.put("strimzi.authorizer.grant.when.no.delegate", "true");

        ProtocolAwareLegacyAclAuthorizer authorizer = new ProtocolAwareLegacyAclAuthorizer();
        authorizer.configure(config);
        authorizer.acls(AclBindingFilter.ANY);
    }

    private Authorizer createAuthorizer() {
        Map<String, String> config = new HashMap<>();
        config.put("authorizer.class.name", ProtocolAwareLegacyAclAuthorizer.class.getTypeName());
        config.put("strimzi.authorizer.delegate.class.name", MockAuthorizer.class.getTypeName());

        Authorizer authorizer = new ProtocolAwareLegacyAclAuthorizer();
        authorizer.configure(config);
        return authorizer;
    }

    private AuthorizableRequestContext requestContext(KafkaPrincipal principal, SecurityProtocol protocol) throws Exception {
        AuthorizableRequestContext ctx = mock(AuthorizableRequestContext.class);
        when(ctx.principal()).thenReturn(principal);
        when(ctx.securityProtocol()).thenReturn(protocol);
        when(ctx.clientAddress()).thenReturn(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        return ctx;
    }

    public static class MockAuthorizer implements Authorizer {
        LinkedList<MockAuthorizerLog> invocationLog = new LinkedList<>();

        public MockAuthorizer() {
            mockAuthorizerTL.set(this);
        }

        @Override
        public List<AuthorizationResult> authorize(AuthorizableRequestContext authorizableRequestContext, List<Action> list) {
            invocationLog.add(new MockAuthorizerLog(authorizableRequestContext));
            return Collections.singletonList(AuthorizationResult.ALLOWED);
        }

        @Override
        public void close() {
        }

        @Override
        public void configure(Map<String, ?> configs) {
            invocationLog.add(new MockAuthorizerLog(null));
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
        private final AuthorizableRequestContext context;

        MockAuthorizerLog(AuthorizableRequestContext requestContext) {
            this.context = requestContext;
        }
    }
}
