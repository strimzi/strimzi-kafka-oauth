/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.services.Services;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.metrics.Metrics;
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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * An authorizer using Keycloak Authorization Services that supports KRaft mode.
 * <p>
 * In KRaft mode multiple instances of this class can be instantiated, and each needs its own instance of <code>StandardAuthorizer</code> for
 * delegating authorization to Kafka ACL implementation.
 * <p>
 * This authorizer automatically sets up appropriate Kafka ACL delegation classes if delegation is enabled.
 * All authorization logic is delegated to <code>KeycloakRBACAuthorizer</code> of which a single instance is created and shared between all
 * instances of this class.
 * <p>
 * To install this authorizer in Kafka, specify the following in your 'server.properties':
 * </p>
 * <pre>
 *     authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer
 *     principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
 * </pre>
 * <p>
 * Configuration options are the same as for {@link KeycloakRBACAuthorizer}.
 */
@SuppressWarnings("deprecation")
public class KeycloakAuthorizer implements ClusterMetadataAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthorizer.class);

    /**
     * A counter used to generate an instance number for each instance of this class
     */
    private final static AtomicInteger INSTANCE_NUMBER_COUNTER = new AtomicInteger(1);

    /**
     * An instance number used in {@link #toString()} method, to easily track the number of instances of this class
     */
    private final int instanceNumber = INSTANCE_NUMBER_COUNTER.getAndIncrement();

    private StandardAuthorizer delegate;
    private Object pluginMetrics;
    private KeycloakRBACAuthorizer singleton;

    @Override
    public void configure(Map<String, ?> configs) {
        Configuration configuration = new Configuration(configs);

        // There is one singleton to which authorize() calls are delegated
        singleton = KeycloakAuthorizerService.getInstance();
        if (singleton == null) {
            singleton = new KeycloakRBACAuthorizer(this);
            singleton.configure(configs);
            KeycloakAuthorizerService.setInstance(singleton);
        } else if (!configuration.equals(singleton.getConfiguration())) {
            throw new ConfigException("Only one authorizer configuration per JVM is supported");
        }

        if (configuration.isDelegateToKafkaACL()) {
            delegate = instantiateStandardAuthorizer();
            delegate.configure(configs);
            initPluginMetricsIfNeeded();
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured " + this + " using " + singleton);
        }
    }

    /**
     * Initialise and set PluginMetrics on StandardAuthorizer if it implements Monitorable
     * Use of reflection is needed since the classes are only available starting with Kafka 4.1.0
     * Once this class implements Monitorable the use of reflection will no longer be necessary, and the passed-in
     * PluginMetrics object will be passed on to StandardAuthorizer.withPluginMetrics().
     */
    private void initPluginMetricsIfNeeded() {
        Class<?> monitorableClass = null;
        try {
            // Check if StandardAuthorizer implements Monitorable, which can only be true if running on Kafka 4.1.0+
            monitorableClass = Class.forName("org.apache.kafka.common.metrics.Monitorable");
        } catch (Exception e) {
            log.debug("Monitorable class not present. PluginMetrics initialisation skipped on StandardAuthorizer");
        }
        try {
            if (monitorableClass != null && monitorableClass.isAssignableFrom(delegate.getClass())) {
                Class<?> pluginClass = Class.forName("org.apache.kafka.common.internals.Plugin");
                Metrics metrics = Services.getInstance().getMetrics().getKafkaMetrics();
                Method method = pluginClass.getMethod("wrapInstance", Object.class, Metrics.class, String.class, String.class, String.class);
                pluginMetrics = method.invoke(null, delegate, metrics, "authorizer.class.name", "role", determineRole());
            }
        } catch (Throwable e) {
            log.warn("Failed to initialise PluginMetrics on StandardAuthorizer", e);
        }
    }

    /**
     * This method uses a rather hackish way to determine if this authorizer instance is instantiated
     * by ControllerServer or by BrokerServer. Once this class implements Monitorable, this will no longer be necessary.
     *
     * @return The role of the instantiating server: "controller" or "broker"
     */
    private String determineRole() {
        for (StackTraceElement s: new RuntimeException().getStackTrace()) {
            String className = s.getClassName();
            if (className.endsWith("ControllerServer")) {
                return "controller";
            } else if (className.endsWith("BrokerServer")) {
                return "broker";
            }
        }
        return "broker";
    }

    /**
     * Invoke Plugin.close() if pluginMetrics was initialised.
     * Use of reflection is needed since the classes are only available starting with Kafka 4.1.0
     */
    private void closePluginMetricsIfNeeded() {
        try {
            if (pluginMetrics != null) {
                Class<?> pluginClass = Class.forName("org.apache.kafka.common.internals.Plugin");
                Method method = pluginClass.getMethod("close");
                method.invoke(pluginMetrics);
            }
        } catch (Throwable e) {
            log.warn("Failed to close PluginMetrics on StandardAuthorizer", e);
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

        closePluginMetricsIfNeeded();
    }

    @Override
    public String toString() {
        return KeycloakAuthorizer.class.getSimpleName() + "@" + instanceNumber;
    }
}
