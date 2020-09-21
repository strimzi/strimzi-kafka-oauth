/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.TimeUtil;
import kafka.network.RequestChannel;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.immutable.Map;
import scala.collection.immutable.Set;

import static io.strimzi.kafka.oauth.common.LogUtil.mask;

/**
 * An authorizer that grants access only if the access token used during SASL_OAUTHBEARER based authentication
 * has not yet expired based on expiry time of the token as set by Authorization Server when the token was issued.
 * <p>
 * This authorizer does not detect if the token was invalidated mid-session by explicitly revoking it at the
 * authorization server or by revoking the JWKS signing keys at the authorization server.
 * <p>
 * To install this authorizer in Kafka broker, specify the following in your 'server.properties':
 * <pre>
 *     authorizer.class.name=io.strimzi.kafka.oauth.server.OAuthSessionAuthorizer
 *     principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
 * </pre>
 * When you would otherwise configure another authorizer, you'll need to specify that other authorizer as the delegate
 * authorizer using the <em>strimzi.authorizer.delegate.class.name</em> configuration property.
 * For example:
 * <pre>
 *     strimzi.authorizer.delegate.class.name=kafka.security.auth.SimpleAclAuthorizer
 * </pre>
 * The specified delegate authorizer should be configured according to its documentation, as if it was installed as the
 * main authorizer (using <em>authorizer.class.name</em>).
 * <p>
 * If the `strimzi.authorizer.delegate.class.name` is not set, then you need to explicitly specify that all authorizations
 * should be granted, as long as the access token has not expired by specifying:
 * <pre>
 *     strimzi.authorizer.grant.when.no.delegate=true
 * </pre>
 * With this setting the OAuthSessionAuthorizer behaves as if there was no authorizer installed - it grants everything with
 * the exception that the sessions using SASL_OAUTHBEARER with expired token will be denied.
 * <p>
 * This authorizer doesn't take <em>super.users</em> setting into account. When used without a delegate every user effectively becomes a super user.
 */
@SuppressWarnings("deprecation")
public class OAuthSessionAuthorizer implements kafka.security.auth.Authorizer {

    static final Logger log = LoggerFactory.getLogger(OAuthSessionAuthorizer.class);
    static final Logger GRANT_LOG = LoggerFactory.getLogger(OAuthSessionAuthorizer.class.getName() + ".grant");
    static final Logger DENY_LOG = LoggerFactory.getLogger(OAuthSessionAuthorizer.class.getName() + ".deny");

    private kafka.security.auth.Authorizer delegate;

    @Override
    public void configure(java.util.Map<String, ?> configs) {
        String className = (String) configs.get(ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME);

        if (className != null) {
            try {
                Class delegateClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                if (!kafka.security.auth.Authorizer.class.isAssignableFrom(delegateClass)) {
                    throw new IllegalArgumentException("The class specified by " + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + " is not an instance of kafka.security.auth.Authorizer");
                }

                delegate = (kafka.security.auth.Authorizer) delegateClass.<kafka.security.auth.Authorizer>newInstance();

                // Configure the delegate
                delegate.configure(configs);

            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed to instantiate and configure the delegate authorizer: " + className, e);
            }
        } else {
            String grantByDefault = (String) configs.get(ServerConfig.STRIMZI_AUTHORIZER_GRANT_WHEN_NO_DELEGATE);
            boolean isGrantByDefault = grantByDefault != null && Config.isTrue(grantByDefault);

            if (!isGrantByDefault) {
                throw new RuntimeException("When no 'strimzi.authorizer.delegate.class.name' is specified, 'strimzi.authorizer.grant.when.no.delegate=true' has to be specified");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured OAuthSessionAuthorizer:"
                    + "\n    " + ServerConfig.STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME + ": " + className);
        }
    }

    @Override
    public boolean authorize(RequestChannel.Session session, kafka.security.auth.Operation operation, kafka.security.auth.Resource resource) {

        KafkaPrincipal principal = session.principal();

        if (!(principal instanceof OAuthKafkaPrincipal)) {
            // If user wasn't authenticated over OAuth, there's nothing for us to check
            if (delegate != null) {
                return delegate.authorize(session, operation, resource);
            } else {
                if (GRANT_LOG.isDebugEnabled()) {
                    GRANT_LOG.debug("Authorization GRANTED - no access token: " + session.principal() + ", operation: " + operation + ", resource: " + resource);
                }
                return true;
            }
        }

        BearerTokenWithPayload token = ((OAuthKafkaPrincipal) principal).getJwt();

        if (denyIfTokenInvalid(token)) {
            return false;
        }

        if (delegate == null) {
            if (GRANT_LOG.isDebugEnabled()) {
                GRANT_LOG.debug("Authorization GRANTED - access token still valid: " + session.principal() + ", operation: " + operation + ", resource: " + resource + ", token: " + mask(token.value()));
            }
            return true;
        }

        return delegate.authorize(session, operation, resource);
    }

    private boolean denyIfTokenInvalid(BearerTokenWithPayload token) {
        if (token.lifetimeMs() <= System.currentTimeMillis()) {
            if (DENY_LOG.isDebugEnabled()) {
                DENY_LOG.debug("Authorization DENIED due to token expiry - The token expired at: "
                        + token.lifetimeMs() + " (" + TimeUtil.formatIsoDateTimeUTC(token.lifetimeMs()) + " UTC), for token: " + mask(token.value()));
            }
            return true;
        }
        return false;
    }

    @Override
    public void addAcls(Set<kafka.security.auth.Acl> acls, kafka.security.auth.Resource resource) {
        delegate.addAcls(acls, resource);
    }

    @Override
    public boolean removeAcls(Set<kafka.security.auth.Acl> acls, kafka.security.auth.Resource resource) {
        return delegate.removeAcls(acls, resource);
    }

    @Override
    public boolean removeAcls(kafka.security.auth.Resource resource) {
        return delegate.removeAcls(resource);
    }

    @Override
    public Set<kafka.security.auth.Acl> getAcls(kafka.security.auth.Resource resource) {
        return delegate.getAcls(resource);
    }

    @Override
    public Map<kafka.security.auth.Resource, Set<kafka.security.auth.Acl>> getAcls(KafkaPrincipal principal) {
        return delegate.getAcls(principal);
    }

    @Override
    public Map<kafka.security.auth.Resource, Set<kafka.security.auth.Acl>> getAcls() {
        return delegate.getAcls();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
