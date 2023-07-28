/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipal;
import io.strimzi.kafka.oauth.server.authorizer.metrics.GrantsHttpSensorKeyProducer;
import io.strimzi.kafka.oauth.server.authorizer.metrics.KeycloakAuthorizationSensorKeyProducer;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
import io.strimzi.kafka.oauth.services.ServiceException;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import kafka.security.authorizer.AclAuthorizer;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.Authorizer;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.HttpUtil.post;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

/**
 * An authorizer that grants access based on security policies managed in Keycloak Authorization Services.
 * It works in conjunction with JaasServerOauthValidatorCallbackHandler, and requires
 * {@link OAuthKafkaPrincipalBuilder} to be configured as
 * 'principal.builder.class' in 'server.properties' file.
 * <p>
 * To install this authorizer in Kafka, specify the following in your 'server.properties':
 * </p>
 * <pre>
 *     authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer
 *     principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder
 * </pre>
 * <p>
 * This authorizer only supports Kafka running in 'zookeeper' mode. It does not support 'KRaft' mode.
 * There is a {@link KeycloakAuthorizer} class that auto-detects the environment and works both in 'KRaft' and 'zookeeper' mode,
 * that should be used instead of this class.
 * </p>
 * <p>
 * There is additional configuration that needs to be specified in order for this authorizer to work.
 * </p>
 * <blockquote>
 * Note: The following configuration keys can be specified as properties in Kafka `server.properties` file, or as
 * ENV vars in which case an all-uppercase key name is also attempted with '.' replaced by '_' (e.g. STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI).
 * They can also be specified as system properties. The priority is in reverse - system property overrides the ENV var, which overrides
 * `server.properties`.
 * </blockquote>
 * <p>
 * Required configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authorization.token.endpoint.uri</em> A URL of the Keycloak's token endpoint (e.g. <code>https://keycloak:8443/auth/realms/master/protocol/openid-connect/token</code>).<br>
 * If not present, <em>oauth.token.endpoint.uri</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authorization.client.id</em> A client id of the OAuth client definition in Keycloak, that has Authorization Services enabled.<br>
 * Typically it is called 'kafka'.
 * If not present, <em>oauth.client.id</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * </ul>
 * <p>
 * Optional configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authorization.kafka.cluster.name</em> The name of this cluster, used to target permissions to specific Kafka cluster, making it possible to manage multiple clusters within the same Keycloak realm.<br>
 * The default value is <em>kafka-cluster</em>.
 * </li>
 * <li><em>strimzi.authorization.delegate.to.kafka.acl</em> Whether authorization decision should be delegated to ACLAuthorizer if DENIED by Keycloak Authorization Services policies.<br>
 * The default value is <em>false</em>.
 * </li>
 * <li><em>strimzi.authorization.grants.refresh.period.seconds</em> The time interval for refreshing the grants of the active sessions. The scheduled job iterates over active sessions and fetches a fresh list of grants for each.<br>
 * The default value is <em>60</em>.
 * </li>
 * <li><em>strimzi.authorization.grants.refresh.pool.size</em> The number of threads used to fetch grants from token endpoint (in parallel).<br>
 * The default value is <em>5</em>.
 * </li>
 * <li><em>strimzi.authorization.reuse.grants</em> Set this option to 'false' if you want every new session to reload and cache new grants for the user rather than using existing ones from the cache.
 * The default value is <em>true</em>
 * </li>
 * <li><em>strimzi.authorization.grants.max.idle.time.seconds</em> The time limit in seconds of a cached grant not being accessed. After that time it will be evicted from grants cache to prevent possibly stale remnant sessions from consuming memory.<br>
 * The default value is <em>300</em>.
 * </li>
 * <li><em>strimzi.authorization.grants.gc.period.seconds</em> A time in seconds between two consecutive runs of the background job that evicts idle or expired grants from cache.<br>
 * The default value is <em>300</em>.
 * </li>
 * <li><em>strimzi.authorization.http.retries</em> The number of times to retry fetch grants from token endpoint.<br>
 * The retry is immediate without pausing due to the authorize() method holding up the Kafka worker thread. The default is <em>0</em> i.e. no retries.
 * </li>
 * <li><em>strimzi.authorization.connect.timeout.seconds</em> The maximum time to wait when establishing the connection to the authorization server.<br>
 * The default value is <em>60</em>.
 * If not present, <em>oauth.connect.timeout.seconds</em> is used as a fallback configuration key to avoid unnecessary duplication when already present.
 * </li>
 * <li><em>strimzi.authorization.read.timeout.seconds</em> The maximum time to wait to read the response from the authorization server after the connection has been established and request sent.<br>
 * The default value is <em>60</em>.
 * If not present, <em>oauth.read.timeout.seconds</em> is used as a fallback configuration key to avoid unnecessary duplication when already present.
 * </li>
 * <li><em>strimzi.authorization.enable.metrics</em> Set this to 'true' to enable authorizer metrics.<br>
 * The default value is <em>false</em>.
 * If not present, <em>oauth.enable.metrics</em> is used as a fallback configuration key to avoid unnecessary duplication when already present as ENV or a system property with intent to enable OAuth and Keycloak authorizer metrics at the broker level.
 * </li>
 * </ul>
 * <p>
 * TLS configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authorization.ssl.truststore.location</em> The location of the truststore file on the filesystem.<br>
 * If not present, <em>oauth.ssl.truststore.location</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authorization.ssl.truststore.password</em> The password for the truststore.<br>
 * If not present, <em>oauth.ssl.truststore.password</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authorization.ssl.truststore.type</em> The truststore type.<br>
 * If not present, <em>oauth.ssl.truststore.type</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the <a href="https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html#getDefaultType--">Java KeyStore default type</a> is used.
 * </li>
 * <li><em>strimzi.authorization.ssl.secure.random.implementation</em> The random number generator implementation. See <a href="https://docs.oracle.com/javase/8/docs/api/java/security/SecureRandom.html#getInstance-java.lang.String-">Java SDK documentation</a>.<br>
 * If not present, <em>oauth.ssl.secure.random.implementation</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the Java platform SDK default is used.
 * </li>
 * <li><em>strimzi.authorization.ssl.endpoint.identification.algorithm</em> Specify how to perform hostname verification. If set to empty string the hostname verification is turned off.<br>
 * If not present, <em>oauth.ssl.endpoint.identification.algorithm</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the default value is <em>HTTPS</em> which enforces hostname verification for server certificates.
 * </li>
 * </ul>
 * <p>
 * This authorizer honors the <em>super.users</em> configuration. Super users are automatically granted any authorization request.
 * </p>
 */
@Deprecated
public class KeycloakRBACAuthorizer implements Authorizer {

    static final Logger log = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class);
    static final Logger GRANT_LOG = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class.getName() + ".grant");
    static final Logger DENY_LOG = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class.getName() + ".deny");

    /**
     * A counter used to generate an instance number for each instance of this class
     */
    private final static AtomicInteger INSTANCE_NUMBER_COUNTER = new AtomicInteger(1);

    /**
     * An instance number used in {@link #toString()} method, to easily track the number of instances of this class
     */
    private final int instanceNumber = INSTANCE_NUMBER_COUNTER.getAndIncrement();

    private final Authorizer delegator;

    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;

    // Turning it to false will not enforce access token expiry time (only for debugging purposes during development)
    private final boolean denyWhenTokenInvalid = true;

    private OAuthMetrics metrics;

    private SensorKeyProducer authzSensorKeyProducer;
    private SensorKeyProducer grantsSensorKeyProducer;

    private Authorizer delegate;

    private GrantsHandler grantsHandler;

    private Configuration configuration;

    /**
     * Create a new instance
     */
    public KeycloakRBACAuthorizer() {
        log.warn("KeycloakRBACAuthorizer has been deprecated, please use '{}' instead.", KeycloakAuthorizer.class.getName());
        this.delegator = null;
    }

    /**
     * Create a new instance, passing a delegating authorizer instance
     *
     * @param delegator The delegating authorizer instance
     */
    KeycloakRBACAuthorizer(Authorizer delegator) {
        this.delegator = delegator;
    }

    @Override
    public void configure(Map<String, ?> configs) {

        configuration = new Configuration(configs);
        configuration.printLogs();

        assignFields(configuration);

        if (log.isDebugEnabled()) {
            log.debug("Configured " + this + (delegator != null ? " (via " + delegator + ")" : "") + ":\n    tokenEndpointUri: " + configuration.getTokenEndpointUrl()
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    clientId: " + configuration.getClientId()
                    + "\n    clusterName: " + configuration.getClusterName()
                    + "\n    delegateToKafkaACL: " + configuration.isDelegateToKafkaACL()
                    + "\n    superUsers: " + configuration.getSuperUsers().stream().map(u -> "'" + u.getType() + ":" + u.getName() + "'").collect(Collectors.toList())
                    + "\n    grantsRefreshPeriodSeconds: " + configuration.getGrantsRefreshPeriodSeconds()
                    + "\n    grantsRefreshPoolSize: " + configuration.getGrantsRefreshPoolSize()
                    + "\n    grantsMaxIdleTimeSeconds: " + configuration.getGrantsMaxIdleTimeSeconds()
                    + "\n    httpRetries: " + configuration.getHttpRetries()
                    + "\n    reuseGrants: " + configuration.isReuseGrants()
                    + "\n    connectTimeoutSeconds: " + configuration.getConnectTimeoutSeconds()
                    + "\n    readTimeoutSeconds: " + configuration.getReadTimeoutSeconds()
                    + "\n    enableMetrics: " + configuration.isEnableMetrics()
                    + "\n    gcPeriodSeconds: " + configuration.getGcPeriodSeconds()
            );
        }
    }

    private void assignFields(Configuration configuration) {
        socketFactory = createSSLFactory(configuration);
        hostnameVerifier = createHostnameVerifier(configuration);

        if (configuration.isDelegateToKafkaACL()) {
            setupDelegateAuthorizer();
        }

        if (!Services.isAvailable()) {
            Services.configure(configuration.getConfigMap());
        }
        if (configuration.isEnableMetrics()) {
            metrics = Services.getInstance().getMetrics();
        }

        authzSensorKeyProducer = new KeycloakAuthorizationSensorKeyProducer("keycloak-authorizer", configuration.getTokenEndpointUrl());
        grantsSensorKeyProducer = new GrantsHttpSensorKeyProducer("keycloak-authorizer", configuration.getTokenEndpointUrl());

        grantsHandler = new GrantsHandler(configuration.getGrantsRefreshPeriodSeconds(),
                configuration.getGrantsRefreshPoolSize(),
                configuration.getGrantsMaxIdleTimeSeconds(),
                this::fetchAuthorizationGrantsOnce,
                configuration.getHttpRetries(),
                configuration.getGcPeriodSeconds());

        // Call configure() on the delegate as the last thing
        if (delegate != null) {
            delegate.configure(configuration.getConfigMap());
        }
    }

    /**
     * This method is only called if <code>delegateToKafkaACL</code> is enabled.
     * It is responsible for instantiating the Authorizer delegate instance.
     */
    void setupDelegateAuthorizer() {
        if (delegate == null && !configuration.isKRaft()) {
            log.debug("Using AclAuthorizer (ZooKeeper based) as a delegate");
            delegate = new AclAuthorizer();
        }
    }

    static SSLSocketFactory createSSLFactory(Configuration config) {
        return SSLUtil.createSSLFactory(config.getTruststore(), config.getTruststoreData(), config.getTruststorePassword(), config.getTruststoreType(), config.getPrng());
    }

    /**
     * This method returning null means that the default certificate hostname validation rules apply
     *
     * @param config Configuration object with parsed configuration
     * @return HostnameVerifier that ignores hostname mismatches in the certificate or null
     */
    static HostnameVerifier createHostnameVerifier(Configuration config) {
        // Following Kafka convention for skipping hostname validation (when set to <empty>)
        return "".equals(config.getCertificateHostCheckAlgorithm()) ? SSLUtil.createAnyHostHostnameVerifier() : null;
    }

    /**
     * The method that makes the authorization decision.
     * <p>
     * We assume authorize() is thread-safe in a sense that there will not be two concurrent threads
     * calling it at the same time for the same session.
     * <p>
     * Should that not be the case, the side effect could be to make more calls to token endpoint than necessary.
     * Other than that it should not affect proper functioning of this authorizer.
     *
     * @param requestContext Request context including request type, security protocol and listener name
     * @param actions Actions being authorized including resource and operation for each action
     * @return List of authorization results for each action in the same order as the provided actions
     */
    public List<AuthorizationResult> authorize(AuthorizableRequestContext requestContext, List<Action> actions) {
        return authorize(delegate, requestContext, actions);
    }

    /**
     * Authorizer method that can be called by another authorizer to delegate the authorization call to a specific delegate instance.
     * It allows multiple authorizer instances to delegate to the same KeycloakRBACAuthorizer instance.
     *
     * @see KeycloakRBACAuthorizer#authorize(AuthorizableRequestContext, List)
     *
     * @param delegate Delegate authorizer to use as fallback
     * @param requestContext Request context including request type, security protocol and listener name
     * @param actions Actions being authorized including resource and operation for each action
     * @return List of authorization results for each action in the same order as the provided actions
     */
    List<AuthorizationResult> authorize(Authorizer delegate, AuthorizableRequestContext requestContext, List<Action> actions) {

        JsonNode grants = null;
        long startTime = System.currentTimeMillis();
        List<AuthorizationResult> result;

        try {
            KafkaPrincipal principal = requestContext.principal();

            for (UserSpec u : configuration.getSuperUsers()) {
                if (principal.getPrincipalType().equals(u.getType())
                        && principal.getName().equals(u.getName())) {

                    for (Action action: actions) {
                        // It's a superuser. Superusers are granted everything
                        if (GRANT_LOG.isDebugEnabled() && action.logIfAllowed()) {
                            GRANT_LOG.debug("Authorization GRANTED - user is a superuser: " + requestContext.principal() +
                                    ", cluster: " + configuration.getClusterName() + ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern()));
                        }
                    }
                    addAuthzMetricSuccessTime(startTime);
                    return Collections.nCopies(actions.size(), AuthorizationResult.ALLOWED);
                }
            }

            if (!(principal instanceof OAuthKafkaPrincipal)) {
                // If user wasn't authenticated over OAuth, and simple ACL delegation is enabled
                // we delegate to simple ACL

                result = delegateIfRequested(delegate, requestContext, actions, null);

                addAuthzMetricSuccessTime(startTime);
                return result;
            }

            //
            // Check if authorization grants are available
            // If not, fetch authorization grants and store them in the grants cache
            //

            BearerTokenWithPayload token = ((OAuthKafkaPrincipal) principal).getJwt();

            if (denyIfTokenInvalid(token)) {
                addAuthzMetricSuccessTime(startTime);
                return Collections.nCopies(actions.size(), AuthorizationResult.DENIED);
            }

            if (grantsHandler == null) {
                throw new IllegalStateException("Authorizer has not been configured - configure() not called");
            }

            GrantsHandler.Info grantsInfo = grantsHandler.getGrantsInfoFromCache(token);
            log.trace("Got grantsInfo: {}", grantsInfo);
            grants = grantsInfo.getGrants();
            boolean newSession = token.getPayload() == null;
            boolean mustReload = !configuration.isReuseGrants() && newSession;
            if (grants == null || mustReload) {
                if (grants == null) {
                    log.debug("No grants yet for user: {}", principal);
                } else {
                    log.debug("Grants available but new session and reuseGrants is `false`");
                }
                grants = grantsHandler.fetchGrantsForUserOrWaitForDelivery(principal.getName(), grantsInfo);
                if (mustReload) {
                    // save empty JSON object as marker that the session has had the grants loaded.
                    token.setPayload(JSONUtil.newObjectNode());
                }
            }
            log.debug("Got grants for '{}': {}", principal, grants);

            if (grants != null) {
                result = allowOrDenyBasedOnGrants(delegate, requestContext, actions, grants);
            } else {
                result = delegateIfRequested(delegate, requestContext, actions, null);
            }
            addAuthzMetricSuccessTime(startTime);
            return result;

        } catch (Throwable t) {
            log.error("An unexpected exception has occurred: ", t);
            if (DENY_LOG.isDebugEnabled()) {
                DENY_LOG.debug("Authorization DENIED due to error - user: " + requestContext.principal() +
                        ", cluster: " + configuration.getClusterName() + ", actions: " + actions + ",\n permissions: " + grants);
            }
            addAuthzMetricErrorTime(t, startTime);

            // We don't rethrow the exception, not even if it is an error.
            // Rethrowing would not trigger JVM shutdown, but it would log the error again, bloating the log file

            return Collections.nCopies(actions.size(), AuthorizationResult.DENIED);
        }
    }

    private String fromResourcePattern(ResourcePattern pattern) {
        return pattern.resourceType() + ":" + pattern.name();
    }


    private List<AuthorizationResult> allowOrDenyBasedOnGrants(Authorizer delegate, AuthorizableRequestContext requestContext, List<Action> actions, JsonNode grants) {
        List<AuthorizationResult> results = new ArrayList<>(actions.size());

        //
        // Iterate authorization rules and try to find a match
        //
        action_loop:
        for (Action action: actions) {
            for (JsonNode permission : grants) {
                String name = permission.get("rsname").asText();
                ResourceSpec resourceSpec = ResourceSpec.of(name);
                if (resourceSpec.match(configuration.getClusterName(), action.resourcePattern().resourceType().name(), action.resourcePattern().name())) {

                    JsonNode scopes = permission.get("scopes");
                    ScopesSpec grantedScopes = scopes == null ? null : ScopesSpec.of(
                            validateScopes(
                                    JSONUtil.asListOfString(scopes)));

                    if (scopes == null || grantedScopes.isGranted(action.operation().name())) {
                        if (GRANT_LOG.isDebugEnabled() && action.logIfAllowed()) {
                            GRANT_LOG.debug("Authorization GRANTED - cluster: " + configuration.getClusterName() + ", user: " + requestContext.principal() +
                                    ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern()) +
                                    "\nGranted scopes for resource (" + resourceSpec + "): " + (grantedScopes == null ? "ALL" : grantedScopes));
                        }
                        results.add(AuthorizationResult.ALLOWED);
                        continue action_loop;
                    }
                }
            }
            results.addAll(delegateIfRequested(delegate, requestContext, Collections.singletonList(action), grants));
        }
        return results;
    }

    private boolean denyIfTokenInvalid(BearerTokenWithPayload token) {
        if (denyWhenTokenInvalid && token.lifetimeMs() <= System.currentTimeMillis()) {
            if (DENY_LOG.isDebugEnabled()) {
                DENY_LOG.debug("Authorization DENIED due to token expiry - The token expired at: "
                        + token.lifetimeMs() + " (" + TimeUtil.formatIsoDateTimeUTC(token.lifetimeMs()) + " UTC), for token: " + mask(token.value()));
            }
            return true;
        }
        return false;
    }

    static List<ScopesSpec.AuthzScope> validateScopes(List<String> scopes) {
        List<ScopesSpec.AuthzScope> enumScopes = new ArrayList<>(scopes.size());
        for (String name: scopes) {
            try {
                enumScopes.add(ScopesSpec.AuthzScope.of(name));
            } catch (Exception e) {
                log.warn("[IGNORED] Invalid scope detected in authorization scopes list: " + name);
            }
        }
        return enumScopes;
    }

    private List<AuthorizationResult> delegateIfRequested(Authorizer delegate, AuthorizableRequestContext context, List<Action> actions, JsonNode authz) {
        String nonAuthMessageFragment = context.principal() instanceof OAuthKafkaPrincipal ? "" : " non-oauth";
        if (delegate != null) {
            List<AuthorizationResult> results = delegate.authorize(context, actions);

            int i = 0;
            for (AuthorizationResult result: results) {
                Action action = actions.get(i);
                boolean grantLogOn = result == AuthorizationResult.ALLOWED && GRANT_LOG.isDebugEnabled() && action.logIfAllowed();
                boolean denyLogOn = result == AuthorizationResult.DENIED && DENY_LOG.isDebugEnabled() && action.logIfDenied();

                if (grantLogOn || denyLogOn) {
                    String status = result == AuthorizationResult.ALLOWED ? "GRANTED" : "DENIED";
                    String message = getACLMessage(context, nonAuthMessageFragment, action, status);

                    if (grantLogOn) {
                        GRANT_LOG.debug(message);
                    } else {
                        DENY_LOG.debug(message);
                    }
                } else if (result == AuthorizationResult.DENIED) {
                    // To ease debugging we log DENIED even if action says to not log it to audit log
                    // We log it to regular logger instead
                    if (log.isDebugEnabled()) {
                        log.debug(getACLMessage(context, nonAuthMessageFragment, action, "DENIED"));
                    }
                }
                i++;
            }
            return results;
        }

        if (DENY_LOG.isDebugEnabled()) {
            for (Action action: actions) {
                if (action.logIfDenied()) {
                    logDenied(DENY_LOG, context, authz, nonAuthMessageFragment, action);
                }
            }
        } else if (log.isDebugEnabled()) {
            // To ease debugging we log DENIED even if action says to not log it to audit log
            // We log it to regular logger instead
            for (Action action: actions) {
                logDenied(log, context, authz, nonAuthMessageFragment, action);
            }
        }
        return Collections.nCopies(actions.size(), AuthorizationResult.DENIED);
    }

    private String getACLMessage(AuthorizableRequestContext context, String nonAuthMessageFragment, Action action, String status) {
        return "Authorization " + status + " by ACL -" + nonAuthMessageFragment + " user: " + context.principal() +
                ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern());
    }

    private void logDenied(Logger logger, AuthorizableRequestContext context, JsonNode authz, String nonAuthMessageFragment, Action action) {
        logger.debug("Authorization DENIED -" + nonAuthMessageFragment + " user: " + context.principal() +
                ", cluster: " + configuration.getClusterName() + ", operation: " + action.operation() +
                ", resource: " + fromResourcePattern(action.resourcePattern()) + ",\n permissions: " + authz);
    }

    private JsonNode fetchAuthorizationGrantsOnce(String token) {

        String authorization = "Bearer " + token;

        StringBuilder body = new StringBuilder("audience=").append(urlencode(configuration.getClientId()))
                .append("&grant_type=").append(urlencode("urn:ietf:params:oauth:grant-type:uma-ticket"))
                .append("&response_mode=permissions");

        JsonNode response;
        long startTime = System.currentTimeMillis();

        try {
            response = post(configuration.getTokenEndpointUrl(), socketFactory, hostnameVerifier, authorization,
                    "application/x-www-form-urlencoded", body.toString(), JsonNode.class, configuration.getConnectTimeoutSeconds(), configuration.getReadTimeoutSeconds(), configuration.includeAcceptHeader());
            addGrantsHttpMetricSuccessTime(startTime);
        } catch (HttpException e) {
            addGrantsHttpMetricErrorTime(e, startTime);
            throw e;
        } catch (Exception e) {
            addGrantsHttpMetricErrorTime(e, startTime);
            throw new ServiceException("Failed to fetch authorization data from authorization server: ", e);
        }

        return response;
    }

    @Override
    public void close() {
        // We don't care about finishing the refresh tasks
        if (grantsHandler != null) {
            try {
                grantsHandler.close();
            } catch (Exception e) {
                log.error("Failed to shutdown the worker pool", e);
            }
        }

        if (delegate != null) {
            try {
                delegate.close();
            } catch (Exception e) {
                log.error("Failed to close the delegate authorizer", e);
            }
        }
    }

    @Override
    public java.util.Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo serverInfo) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (delegate == null) {
            return serverInfo.endpoints().stream().collect(Collectors.toMap(Function.identity(), e -> future));
        }
        return delegate.start(serverInfo);
    }

    @Override
    public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext requestContext, List<AclBinding> aclBindings) {
        if (delegate == null) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return delegate.createAcls(requestContext, aclBindings);
    }

    @Override
    public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext requestContext, List<AclBindingFilter> aclBindingFilters) {
        if (delegate == null) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return delegate.deleteAcls(requestContext, aclBindingFilters);
    }

    @Override
    public Iterable<AclBinding> acls(AclBindingFilter filter) {
        if (delegate == null) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return delegate.acls(filter);
    }

    private void addAuthzMetricSuccessTime(long startTimeMs) {
        if (configuration.isEnableMetrics()) {
            metrics.addTime(authzSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addAuthzMetricErrorTime(Throwable e, long startTimeMs) {
        if (configuration.isEnableMetrics()) {
            metrics.addTime(authzSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addGrantsHttpMetricSuccessTime(long startTimeMs) {
        if (configuration.isEnableMetrics()) {
            metrics.addTime(grantsSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addGrantsHttpMetricErrorTime(Throwable e, long startTimeMs) {
        if (configuration.isEnableMetrics()) {
            metrics.addTime(grantsSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }

    Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return KeycloakRBACAuthorizer.class.getSimpleName() + "@" + instanceNumber;
    }
}
