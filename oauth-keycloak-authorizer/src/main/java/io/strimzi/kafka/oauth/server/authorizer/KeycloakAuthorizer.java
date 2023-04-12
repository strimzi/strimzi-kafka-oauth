/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.metadata.authorizer.AclMutator;
import org.apache.kafka.metadata.authorizer.ClusterMetadataAuthorizer;
import org.apache.kafka.metadata.authorizer.StandardAcl;
import org.apache.kafka.metadata.authorizer.StandardAuthorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The Keycloak authorizer implementation that supports KRaft mode and delegates to
 * <code>org.apache.kafka.metadata.authorizer.StandardAuthorizer</code> if <code>strimzi.authorization.delegate.to.kafka.acl</code>
 * is set to <code>true</code>.
 * <p>
 * This authorizer auto-detects whether the broker runs in KRaft mode or not based on the presence and value of <code>process.roles</code> config option.
 * When in KRaft mode the authorizer relies on <code>strimzi.authorization.reuse.grants</code> behaviour, and automatically enables this mode.
 * <p>
 * KeycloakAuthorizer works in conjunction with JaasServerOauthValidatorCallbackHandler, and requires
 * {@link OAuthKafkaPrincipalBuilder} to be configured as 'principal.builder.class' in 'server.properties' file.
 * <p>
 * To install this authorizer in Kafka, specify the following in your 'server.properties':
 * </p>
 * <pre>
 *     authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer
 *     principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
 * </pre>
 * The functionality of this authorizer is mostly inherited from <code>KeycloakRBACAuthorizer</code>.
 * For more configuration options see README.md and {@link io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer}.
 */
public class KeycloakAuthorizer extends KeycloakRBACAuthorizer implements ClusterMetadataAuthorizer {

    static final Logger log = LoggerFactory.getLogger(KeycloakAuthorizer.class);

    private ClusterMetadataAuthorizer kraftAuthorizer;

    private boolean isKRaft;
    private AclMutator mutator;

    @Override
    AuthzConfig convertToAuthzConfig(Map<String, ?> configs) {
        AuthzConfig superConfig = super.convertToAuthzConfig(configs);
        isKRaft = detectKRaft(configs);
        if (isKRaft) {
            log.debug("Detected KRaft mode ('process.roles' configured)");
            return new AuthzConfigWithForcedReuseGrants(superConfig);
        }
        return superConfig;
    }

    boolean detectKRaft(Map<String, ?> configs) {
        // auto-detect KRaft mode
        Object prop = configs.get("process.roles");
        String processRoles = prop != null ? String.valueOf(prop) : null;
        return processRoles != null && processRoles.length() > 0;
    }

    @Override
    void setupDelegateAuthorizer(Map<String, ?> configs) {
        if (isKRaft) {
            try {
                kraftAuthorizer = new StandardAuthorizer();
                setDelegate(kraftAuthorizer);
                log.debug("Using StandardAuthorizer (KRaft based) as a delegate");
            } catch (Exception e) {
                throw new ConfigException("KRaft mode detected ('process.roles' configured), but failed to instantiate org.apache.kafka.metadata.authorizer.StandardAuthorizer", e);
            }
        }
        super.setupDelegateAuthorizer(configs);
    }

    /**
     * Set the mutator object which should be used for creating and deleting ACLs.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    // See https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei2-may-expose-internal-representation-by-incorporating-reference-to-mutable-object-ei-expose-rep2
    public void setAclMutator(AclMutator aclMutator) {
        if (kraftAuthorizer != null) {
            kraftAuthorizer.setAclMutator(aclMutator);
        }
        this.mutator = aclMutator;
    }

    /**
     * Get the mutator object which should be used for creating and deleting ACLs.
     *
     * @throws org.apache.kafka.common.errors.NotControllerException
     *              If the aclMutator was not set.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    // See https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ei-may-expose-internal-representation-by-returning-reference-to-mutable-object-ei-expose-rep
    public AclMutator aclMutatorOrException() {
        if (kraftAuthorizer != null) {
            return kraftAuthorizer.aclMutatorOrException();
        }
        return mutator;
    }

    /**
     * Complete the initial load of the cluster metadata authorizer, so that all
     * principals can use it.
     */
    public void completeInitialLoad() {
        if (kraftAuthorizer != null) {
            kraftAuthorizer.completeInitialLoad();
        }
    }

    /**
     * Complete the initial load of the cluster metadata authorizer with an exception,
     * indicating that the loading process has failed.
     */
    public void completeInitialLoad(Exception e) {
        if (kraftAuthorizer != null) {
            kraftAuthorizer.completeInitialLoad(e);
        }
        log.error("Failed to load authorizer cluster metadata", e);
    }

    /**
     * Load the ACLs in the given map. Anything not in the map will be removed.
     * The authorizer will also wait for this initial snapshot load to complete when
     * coming up.
     */
    public void loadSnapshot(Map<Uuid, StandardAcl> acls) {
        if (kraftAuthorizer != null) {
            kraftAuthorizer.loadSnapshot(acls);
        }
    }

    /**
     * Add a new ACL. Any ACL with the same ID will be replaced.
     */
    public void addAcl(Uuid id, StandardAcl acl) {
        if (kraftAuthorizer == null) {
            throw new UnsupportedOperationException("StandardAuthorizer ACL delegation not enabled");
        }
        kraftAuthorizer.addAcl(id, acl);
    }

    /**
     * Remove the ACL with the given ID.
     */
    public void removeAcl(Uuid id) {
        if (kraftAuthorizer == null) {
            throw new UnsupportedOperationException("StandardAuthorizer ACL delegation not enabled");
        }
        kraftAuthorizer.removeAcl(id);
    }

    private static class AuthzConfigWithForcedReuseGrants extends AuthzConfig {
        AuthzConfigWithForcedReuseGrants(Config superConfig) {
            super(superConfig);
        }

        @Override
        public String getValue(String key, String fallback) {
            if (AuthzConfig.STRIMZI_AUTHORIZATION_REUSE_GRANTS.equals(key)) {
                log.debug("Configuration option '" + AuthzConfig.STRIMZI_AUTHORIZATION_REUSE_GRANTS + "' forced to 'true'");
                return "true";
            }
            return super.getValue(key, fallback);
        }
    }
}
