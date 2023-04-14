/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.ConfigException;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.metadata.authorizer.AclMutator;
import org.apache.kafka.metadata.authorizer.ClusterMetadataAuthorizer;
import org.apache.kafka.metadata.authorizer.StandardAcl;
import org.apache.kafka.metadata.authorizer.StandardAuthorizer;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * An authorizer that ensures a single stateful instance is used for authorization callbacks.
 */
public class KeycloakAuthorizer implements ClusterMetadataAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthorizer.class);

    private final static AtomicInteger VERSION_COUNTER = new AtomicInteger(1);
    private final int version = VERSION_COUNTER.getAndIncrement();
    private StandardAuthorizer delegate;
    private KeycloakRBACAuthorizer singleton;

    @Override
    public void configure(Map<String, ?> configs) {
        Configuration configuration = new Configuration(configs);

        // There is one singleton to which authorize() calls are delegated
        singleton = KeycloakAuthorizerService.getInstance();
        if (singleton == null) {
            singleton = new KeycloakRBACAuthorizer();
            singleton.configure(configs);
            KeycloakAuthorizerService.setInstance(singleton);
        } else if (!configuration.equals(singleton.getConfiguration())) {
            throw new ConfigException("Only one authorizer configuration per JVM is supported");
        }

        if (configuration.isDelegateToKafkaACL() && configuration.isKRaft()) {
            delegate = instantiateStandardAuthorizer();
            delegate.configure(configs);
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured " + this + " using " + singleton);
        }
    }

    private StandardAuthorizer instantiateStandardAuthorizer() {
        try {
            log.debug("Using StandardAuthorizer (KRaft based) as a delegate");
            return new StandardAuthorizer();
        } catch (Exception e) {
            throw new ConfigException("KRaft mode detected ('process.roles' configured), but failed to instantiate org.apache.kafka.metadata.authorizer.StandardAuthorizer", e);
        }
    }

    @Override
    public Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo serverInfo) {
        if (delegate != null) {
            return delegate.start(serverInfo);
        }
        return singleton.start(serverInfo);
    }

    @Override
    public void setAclMutator(AclMutator aclMutator) {
        if (delegate != null) {
            delegate.setAclMutator(aclMutator);
        }
    }

    @Override
    public AclMutator aclMutatorOrException() {
        if (delegate != null) {
            return delegate.aclMutatorOrException();
        }
        throw new IllegalStateException("KeycloakAuthorizer has not been properly configured");
    }

    @Override
    public void completeInitialLoad() {
        if (delegate != null) {
            delegate.completeInitialLoad();
        }
    }

    @Override
    public void completeInitialLoad(Exception e) {
        if (e != null) {
            e.printStackTrace();
        }
        if (delegate != null) {
            delegate.completeInitialLoad(e);
        }
    }

    @Override
    public void loadSnapshot(Map<Uuid, StandardAcl> acls) {
        if (delegate != null) {
            delegate.loadSnapshot(acls);
        }
    }

    @Override
    public void addAcl(Uuid id, StandardAcl acl) {
        if (delegate != null) {
            delegate.addAcl(id, acl);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    @Override
    public void removeAcl(Uuid id) {
        if (delegate != null) {
            delegate.removeAcl(id);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    @Override
    public Iterable<AclBinding> acls(AclBindingFilter filter) {
        if (delegate != null) {
            return delegate.acls(filter);
        } else if (singleton != null) {
            return singleton.acls(filter);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    @Override
    public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext requestContext, List<AclBinding> aclBindings) {
        if (delegate != null) {
            return delegate.createAcls(requestContext, aclBindings);
        } else if (singleton != null) {
            return singleton.createAcls(requestContext, aclBindings);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }


    @Override
    public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext requestContext, List<AclBindingFilter> aclBindingFilters) {
        if (delegate != null) {
            return delegate.deleteAcls(requestContext, aclBindingFilters);
        } else if (singleton != null) {
            return singleton.deleteAcls(requestContext, aclBindingFilters);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    @Override
    public int aclCount() {
        if (delegate != null) {
            return delegate.aclCount();
        } else if (singleton != null) {
            return singleton.aclCount();
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    public AuthorizationResult authorizeByResourceType(AuthorizableRequestContext requestContext, AclOperation op, ResourceType resourceType) {
        if (delegate != null) {
            return delegate.authorizeByResourceType(requestContext, op, resourceType);
        } else if (singleton != null) {
            return singleton.authorizeByResourceType(requestContext, op, resourceType);
        } else {
            throw new UnsupportedOperationException("ACL delegation not enabled");
        }
    }

    @Override
    public List<AuthorizationResult> authorize(AuthorizableRequestContext requestContext, List<Action> actions) {
        if (delegate != null) {
            return singleton.authorize(delegate, requestContext, actions);
        } else {
            return singleton.authorize(requestContext, actions);
        }
    }

    @Override
    public void close() throws IOException {
        if (singleton != null) {
            singleton.close();
        }
        if (delegate != null) {
            delegate.close();
        }
    }

    @Override
    public String toString() {
        return KeycloakAuthorizer.class.getSimpleName() + "@" + version;
    }
}
