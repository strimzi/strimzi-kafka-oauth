/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.Authorizer;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * A protocol-aware authorizer for legacy ACL compatibility.
 * <p>
 * For requests over SASL_PLAINTEXT or SASL_SSL, it strips '#clid:&lt;client-id&gt;' suffix from principal before delegating
 * to the configured Kafka ACL authorizer. For other protocols it delegates with original principal unchanged.
 * </p>
 * <p>
 */
public class ProtocolAwareLegacyAclAuthorizer implements Authorizer {

    static final Logger log = LoggerFactory.getLogger(ProtocolAwareLegacyAclAuthorizer.class);
    static final Logger GRANT_LOG = LoggerFactory.getLogger(ProtocolAwareLegacyAclAuthorizer.class.getName() + ".grant");

    private Authorizer delegate;

    @Override
    public void configure(java.util.Map<String, ?> configs) {
        String className = (String) configs.get(ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME);

        if (className != null) {
            try {
                Class<?> delegateClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                if (!Authorizer.class.isAssignableFrom(delegateClass)) {
                    throw new IllegalArgumentException("The class specified by " + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + " is not an instance of org.apache.kafka.server.authorizer.Authorizer");
                }

                delegate = (Authorizer) delegateClass.getConstructor().newInstance();
                delegate.configure(configs);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new ConfigException("Failed to instantiate and configure the delegate authorizer: " + className, e);
            }
        } else {
            String grantByDefault = (String) configs.get(ServerConfig.STRIMZI_AUTHORIZER_GRANT_WHEN_NO_DELEGATE);
            boolean isGrantByDefault = grantByDefault != null && Config.isTrue(grantByDefault);

            if (!isGrantByDefault) {
                throw new ConfigException("When no 'strimzi.authorizer.delegate.class.name' is specified, 'strimzi.authorizer.grant.when.no.delegate=true' has to be specified");
            }
        }
    }

    @Override
    public List<AuthorizationResult> authorize(AuthorizableRequestContext requestContext, List<Action> actions) {
        KafkaPrincipal principal = requestContext.principal();

        if (delegate == null) {
            if (GRANT_LOG.isDebugEnabled()) {
                GRANT_LOG.debug("Authorization GRANTED - no delegate configured: " + principal + ", actions: " + actions);
            }
            return Collections.nCopies(actions.size(), AuthorizationResult.ALLOWED);
        }

        KafkaPrincipal effectivePrincipal = maybeNormalizeLegacyPrincipal(requestContext, principal);
        AuthorizableRequestContext effectiveContext = effectivePrincipal == principal ? requestContext : wrapWithPrincipal(requestContext, effectivePrincipal);
        return delegate.authorize(effectiveContext, actions);
    }

    private KafkaPrincipal maybeNormalizeLegacyPrincipal(AuthorizableRequestContext requestContext, KafkaPrincipal principal) {
        if (principal == null || principal.getName() == null) {
            return principal;
        }
        SecurityProtocol protocol = requestContext.securityProtocol();
        if (!isLegacyProtocol(protocol)) {
            return principal;
        }
        String name = principal.getName();
        int idx = name.indexOf("#clid:");
        if (idx <= 0) {
            return principal;
        }
        String normalized = name.substring(0, idx);
        log.info("Legacy ACL compatibility applied for protocol {}: principal normalized from '{}' to '{}'", protocol, name, normalized);
        return new KafkaPrincipal(principal.getPrincipalType(), normalized, principal.tokenAuthenticated());
    }

    private boolean isLegacyProtocol(SecurityProtocol securityProtocol) {
        return securityProtocol == SecurityProtocol.SASL_PLAINTEXT || securityProtocol == SecurityProtocol.SASL_SSL;
    }

    private AuthorizableRequestContext wrapWithPrincipal(AuthorizableRequestContext original, KafkaPrincipal principal) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("principal".equals(method.getName()) && method.getParameterCount() == 0) {
                return principal;
            }
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (AuthorizableRequestContext) Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                new Class<?>[]{AuthorizableRequestContext.class},
                handler);
    }

    @Override
    public void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
    }

    @Override
    public java.util.Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo serverInfo) {
        return delegate != null ? delegate.start(serverInfo) : Collections.emptyMap();
    }

    @Override
    public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext requestContext, List<AclBinding> aclBindings) {
        if (delegate == null) {
            throw new ConfigException("ACL management requires '" + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + "' to be configured");
        }
        return delegate.createAcls(requestContext, aclBindings);
    }

    @Override
    public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext requestContext, List<AclBindingFilter> aclBindingFilters) {
        if (delegate == null) {
            throw new ConfigException("ACL management requires '" + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + "' to be configured");
        }
        return delegate.deleteAcls(requestContext, aclBindingFilters);
    }

    @Override
    public Iterable<AclBinding> acls(AclBindingFilter filter) {
        if (delegate == null) {
            throw new ConfigException("ACL management requires '" + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + "' to be configured");
        }
        return delegate.acls(filter);
    }
}
