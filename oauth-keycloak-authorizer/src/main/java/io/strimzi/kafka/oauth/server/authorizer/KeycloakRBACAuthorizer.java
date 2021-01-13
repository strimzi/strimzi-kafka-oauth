/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipal;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.services.SessionFuture;
import io.strimzi.kafka.oauth.services.Sessions;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import io.strimzi.kafka.oauth.validator.DaemonThreadFactory;
import kafka.network.RequestChannel;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.immutable.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
 * <li><em>strimzi.authorization.token.endpoint.uri</em> A URL of the Keycloak's token endpoint (e.g. https://keycloak:8443/auth/realms/master/protocol/openid-connect/token).<br>
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
 * The default value is <em>kafka-cluster</em>
 * </li>
 * <li><em>strimzi.authorization.delegate.to.kafka.acl</em> Whether authorization decision should be delegated to SimpleACLAuthorizer if DENIED by Keycloak Authorization Services policies.<br>
 * The default value is <em>false</em>
 * </li>
 * </ul>
 * <ul>
 * <li><em>strimzi.authorization.grants.refresh.period.seconds</em> The time interval for refreshing the grants of the active sessions. The scheduled job iterates over active sessions and fetches a fresh list of grants for each.<br>
 * The default value is <em>60</em>
 * </li>
 * <li><em>strimzi.authorization.grants.refresh.pool.size</em> The number of threads to fetch grants from token endpoint (in parallel).<br>
 * The default value is <em>5</em>
 * </li>
 * </ul> *
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
@SuppressWarnings("deprecation")
public class KeycloakRBACAuthorizer extends kafka.security.auth.SimpleAclAuthorizer {

    private static final String PRINCIPAL_BUILDER_CLASS = OAuthKafkaPrincipalBuilder.class.getName();
    private static final String DEPRECATED_PRINCIPAL_BUILDER_CLASS = JwtKafkaPrincipalBuilder.class.getName();

    static final Logger log = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class);
    static final Logger GRANT_LOG = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class.getName() + ".grant");
    static final Logger DENY_LOG = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class.getName() + ".deny");

    private URI tokenEndpointUrl;
    private String clientId;
    private String clusterName;
    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;
    private List<UserSpec> superUsers = Collections.emptyList();
    private boolean delegateToKafkaACL = false;

    // Turning it to false will not enforce access token expiry time (only for debugging purposes during development)
    private boolean denyWhenTokenInvalid = true;

    // Less or equal zero means to never check
    private int grantsRefreshPeriodSeconds;

    // Number of threads that can perform token endpoint requests at the same time
    private int grantsRefreshPoolSize;

    private ScheduledExecutorService periodicScheduler;
    private ExecutorService workerPool;


    public KeycloakRBACAuthorizer() {
        super();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        super.configure(configs);

        AuthzConfig config = convertToCommonConfig(configs);

        String pbclass = (String) configs.get("principal.builder.class");
        if (!PRINCIPAL_BUILDER_CLASS.equals(pbclass) && !DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            throw new RuntimeException("KeycloakRBACAuthorizer requires " + PRINCIPAL_BUILDER_CLASS + " as 'principal.builder.class'");
        }

        if (DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            log.warn("The '" + DEPRECATED_PRINCIPAL_BUILDER_CLASS + "' class has been deprecated, and may be removed in the future. Please use '" + PRINCIPAL_BUILDER_CLASS + "' as 'principal.builder.class' instead.");
        }

        String endpoint = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
                ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);
        if (endpoint == null) {
            throw new RuntimeException("OAuth2 Token Endpoint ('strimzi.authorization.token.endpoint.uri') not set.");
        }

        try {
            tokenEndpointUrl = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Specified token endpoint uri is invalid: " + endpoint);
        }

        clientId = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_CLIENT_ID, ClientConfig.OAUTH_CLIENT_ID);
        if (clientId == null) {
            throw new RuntimeException("OAuth2 Client Id ('strimzi.authorization.client.id') not set.");
        }

        socketFactory = createSSLFactory(config);
        hostnameVerifier = createHostnameVerifier(config);

        clusterName = config.getValue(AuthzConfig.STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME);
        if (clusterName == null) {
            clusterName = "kafka-cluster";
        }

        delegateToKafkaACL = config.getValueAsBoolean(AuthzConfig.STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL, false);

        String users = (String) configs.get("super.users");
        if (users != null) {
            superUsers = Arrays.asList(users.split(";"))
                    .stream()
                    .map(s -> UserSpec.of(s))
                    .collect(Collectors.toList());
        }

        grantsRefreshPoolSize = config.getValueAsInt(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE, 5);
        if (grantsRefreshPoolSize < 1) {
            throw new RuntimeException("Invalid value of 'strimzi.authorization.grants.refresh.pool.size': " + grantsRefreshPoolSize + ". Has to be >= 1.");
        }
        grantsRefreshPeriodSeconds = config.getValueAsInt(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, 60);

        if (grantsRefreshPeriodSeconds > 0) {
            workerPool = Executors.newFixedThreadPool(grantsRefreshPoolSize);
            periodicScheduler = setupRefreshGrantsJob(grantsRefreshPeriodSeconds);
        }

        if (!Services.isAvailable()) {
            Services.configure(configs);
        }

        if (log.isDebugEnabled()) {
            log.debug("Configured KeycloakRBACAuthorizer:\n    tokenEndpointUri: " + tokenEndpointUrl
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    clientId: " + clientId
                    + "\n    clusterName: " + clusterName
                    + "\n    delegateToKafkaACL: " + delegateToKafkaACL
                    + "\n    superUsers: " + superUsers.stream().map(u -> "'" + u.getType() + ":" + u.getName() + "'").collect(Collectors.toList())
                    + "\n    grantsRefreshPeriodSeconds: " + grantsRefreshPeriodSeconds
                    + "\n    grantsRefreshPoolSize: " + grantsRefreshPoolSize
            );
        }
    }

    /**
     * This method transforms strimzi.authorization.* entries into oauth.* entries in order to be able to use existing ConfigUtil
     * methods for setting up certificate truststore and hostname verification.
     *
     * It also makes sure to copy over 'as-is' all the config keys expected in server.properties for configuring
     * this authorizer.
     *
     * @param configs Kafka configs map
     * @return Config object
     */
    static AuthzConfig convertToCommonConfig(Map<String, ?> configs) {
        Properties p = new Properties();

        // If you add a new config property, make sure to add it to this list
        // otherwise it won't be picked
        String[] keys = {
            AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS,
            AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE,
            AuthzConfig.STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL,
            AuthzConfig.STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME,
            AuthzConfig.STRIMZI_AUTHORIZATION_CLIENT_ID,
            AuthzConfig.OAUTH_CLIENT_ID,
            AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
            ClientConfig.OAUTH_TOKEN_ENDPOINT_URI,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION,
            Config.OAUTH_SSL_TRUSTSTORE_LOCATION,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD,
            Config.OAUTH_SSL_TRUSTSTORE_PASSWORD,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE,
            Config.OAUTH_SSL_TRUSTSTORE_TYPE,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION,
            Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM
        };

        // Copy over the keys
        for (String key: keys) {
            ConfigUtil.putIfNotNull(p, key, configs.get(key));
        }

        return new AuthzConfig(p);
    }

    static SSLSocketFactory createSSLFactory(Config config) {
        String truststore = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION, Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String password = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD, Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE, Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION, Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, password, type, rnd);
    }

    static HostnameVerifier createHostnameVerifier(Config config) {
        String hostCheck = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM);

        if (hostCheck == null) {
            hostCheck = "HTTPS";
        }
        // Following Kafka convention for skipping hostname validation (when set to <empty>)
        return "".equals(hostCheck) ? SSLUtil.createAnyHostHostnameVerifier() : null;
    }

    /**
     * The method that makes the authorization decision.
     *
     * We assume authorize() is thread-safe in a sense that there will not be two concurrent threads
     * calling it at the same time for the same session.
     *
     * Should that not be the case, the side effect could be to make more calls to token endpoint than necessary.
     * Other than that it should not affect proper functioning of this authorizer.
     *
     * @param session Current session
     * @param operation Operation to authorize
     * @param resource Resource to authorize
     * @return true if permission is granted
     */
    @Override
    public boolean authorize(RequestChannel.Session session, kafka.security.auth.Operation operation, kafka.security.auth.Resource resource) {

        JsonNode grants = null;

        try {
            KafkaPrincipal principal = session.principal();

            for (UserSpec u : superUsers) {
                if (principal.getPrincipalType().equals(u.getType())
                        && principal.getName().equals(u.getName())) {

                    // It's a super user. super users are granted everything
                    if (GRANT_LOG.isDebugEnabled()) {
                        GRANT_LOG.debug("Authorization GRANTED - user is a superuser: " + session.principal() + ", operation: " + operation + ", resource: " + resource);
                    }
                    return true;
                }
            }

            if (!(principal instanceof OAuthKafkaPrincipal)) {
                // If user wasn't authenticated over OAuth, and simple ACL delegation is enabled
                // we delegate to simple ACL
                return delegateIfRequested(session, operation, resource, null);
            }

            //
            // Check if authorization grants are available
            // If not, fetch authorization grants and store them in the token
            //

            OAuthKafkaPrincipal jwtPrincipal = (OAuthKafkaPrincipal) principal;

            BearerTokenWithPayload token = jwtPrincipal.getJwt();

            if (denyIfTokenInvalid(token)) {
                return false;
            }

            grants = (JsonNode) token.getPayload();

            if (grants == null) {
                grants = handleFetchingGrants(token);
            }

            if (log.isDebugEnabled()) {
                log.debug("Authorization grants for user {}: {}", principal, grants);
            }

            //
            // Iterate authorization rules and try to find a match
            //

            if (grants != null) {
                for (JsonNode permission: grants) {
                    String name = permission.get("rsname").asText();
                    ResourceSpec resourceSpec = ResourceSpec.of(name);
                    if (resourceSpec.match(clusterName, resource.resourceType().name(), resource.name())) {

                        ScopesSpec grantedScopes = ScopesSpec.of(
                                validateScopes(
                                        JSONUtil.asListOfString(permission.get("scopes"))));

                        if (grantedScopes.isGranted(operation.name())) {
                            if (GRANT_LOG.isDebugEnabled()) {
                                GRANT_LOG.debug("Authorization GRANTED - cluster: " + clusterName + ", user: " + session.principal() + ", operation: " + operation +
                                        ", resource: " + resource + "\nGranted scopes for resource (" + resourceSpec + "): " + grantedScopes);
                            }
                            return true;
                        }
                    }
                }
            }

            return delegateIfRequested(session, operation, resource, grants);

        } catch (Throwable t) {
            log.error("An unexpected exception has occurred: ", t);
            if (DENY_LOG.isDebugEnabled()) {
                DENY_LOG.debug("Authorization DENIED due to error - user: " + session.principal() +
                        ", cluster: " + clusterName + ", operation: " + operation + ", resource: " + resource + ",\n permissions: " + grants);
            }
            return false;
        }
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

    private JsonNode handleFetchingGrants(BearerTokenWithPayload token) {
        // Fetch authorization grants
        JsonNode grants = null;
        try {
            grants = fetchAuthorizationGrants(token.value());
            if (grants == null) {
                grants = JSONUtil.newObjectNode();
            }
        } catch (HttpException e) {
            if (e.getStatus() == 403) {
                grants = JSONUtil.newObjectNode();
            } else {
                log.warn("Unexpected status while fetching authorization data - will retry next time: " + e.getMessage());
            }
        }
        if (grants != null) {
            // Store authz grants in the token so they are available for subsequent requests
            token.setPayload(grants);
        }
        return grants;
    }

    static List<ScopesSpec.AuthzScope> validateScopes(List<String> scopes) {
        List<ScopesSpec.AuthzScope> enumScopes = new ArrayList<>(scopes.size());
        for (String name: scopes) {
            try {
                enumScopes.add(ScopesSpec.AuthzScope.valueOf(name));
            } catch (Exception e) {
                log.warn("[IGNORED] Invalid scope detected in authorization scopes list: " + name);
            }
        }
        return enumScopes;
    }

    private boolean delegateIfRequested(RequestChannel.Session session, kafka.security.auth.Operation operation, kafka.security.auth.Resource resource, JsonNode authz) {
        String nonAuthMessageFragment = session.principal() instanceof OAuthKafkaPrincipal ? "" : " non-oauth";
        if (delegateToKafkaACL) {
            boolean granted = super.authorize(session, operation, resource);

            boolean grantLogOn = granted && GRANT_LOG.isDebugEnabled();
            boolean denyLogOn = !granted && DENY_LOG.isDebugEnabled();

            if (grantLogOn || denyLogOn) {
                String status = granted ? "GRANTED" : "DENIED";
                String message = "Authorization " + status + " by ACL -" + nonAuthMessageFragment + " user: " + session.principal() + ", operation: " + operation + ", resource: " + resource;

                if (grantLogOn) {
                    GRANT_LOG.debug(message);
                } else if (denyLogOn) {
                    DENY_LOG.debug(message);
                }
            }
            return granted;
        }

        if (DENY_LOG.isDebugEnabled()) {
            DENY_LOG.debug("Authorization DENIED -" + nonAuthMessageFragment + " user: " + session.principal() +
                    ", cluster: " + clusterName + ", operation: " + operation + ", resource: " + resource + ",\n permissions: " + authz);
        }
        return false;
    }

    private JsonNode fetchAuthorizationGrants(String token) {

        String authorization = "Bearer " + token;

        StringBuilder body = new StringBuilder("audience=").append(urlencode(clientId))
                .append("&grant_type=").append(urlencode("urn:ietf:params:oauth:grant-type:uma-ticket"))
                .append("&response_mode=permissions");

        JsonNode response;

        try {
            response = post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization,
                    "application/x-www-form-urlencoded", body.toString(), JsonNode.class);

        } catch (HttpException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch authorization data from authorization server: ", e);
        }

        return response;
    }

    private ScheduledExecutorService setupRefreshGrantsJob(int refreshSeconds) {
        // Set up periodic timer to fetch grants for active sessions every refresh seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

        scheduler.scheduleAtFixedRate(() -> {
            refreshGrants();
        }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);

        return scheduler;
    }

    private void refreshGrants() {
        try {
            log.debug("Refreshing authorization grants ...");
            // Multiple sessions can be authenticated with the same access token
            // Only make one grants request for one unique access_token,
            // but update all sessions for the same token
            ConcurrentHashMap<String, ConcurrentLinkedQueue<BearerTokenWithPayload>> tokens = new ConcurrentHashMap<>();

            Predicate<BearerTokenWithPayload> filter = token -> {
                ConcurrentLinkedQueue<BearerTokenWithPayload> queue = tokens.computeIfAbsent(token.value(), k -> {
                    ConcurrentLinkedQueue<BearerTokenWithPayload> q = new ConcurrentLinkedQueue<>();
                    q.add(token);
                    return q;
                });

                // If we are the first for the access_token return true
                // if not, add the token to the queue, and return false
                if (token != queue.peek()) {
                    queue.add(token);
                    return false;
                }
                return true;
            };

            Sessions sessions = Services.getInstance().getSessions();
            List<SessionFuture> scheduled = scheduleGrantsRefresh(filter, sessions);

            for (SessionFuture f: scheduled) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.warn("[IGNORED] Failed to fetch grants for token: " + e.getMessage(), e);
                    final Throwable cause = e.getCause();
                    if (cause instanceof HttpException) {
                        if (401 == ((HttpException) cause).getStatus()) {
                            JsonNode emptyGrants = JSONUtil.newObjectNode();
                            ConcurrentLinkedQueue<BearerTokenWithPayload> queue = tokens.get(f.getToken().value());
                            for (BearerTokenWithPayload token: queue) {
                                token.setPayload(emptyGrants);
                                sessions.remove(token);
                                if (log.isDebugEnabled()) {
                                    log.debug("Removed invalid session from sessions map (session: {}, token: {}). Will not refresh its grants any more.",
                                            f.getToken().getSessionId(), mask(f.getToken().value()));
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    log.warn("[IGNORED] Failed to fetch grants for session: " + f.getToken().getSessionId() + ", token: " + mask(f.getToken().value()) + " - " + e.getMessage(), e);
                }
            }

            // Go over tokens, and copy the grants from the first session
            // for same access token to all the others
            for (ConcurrentLinkedQueue<BearerTokenWithPayload> q: tokens.values()) {
                BearerTokenWithPayload refreshed = null;
                for (BearerTokenWithPayload t: q) {
                    if (refreshed == null) {
                        refreshed = t;
                        continue;
                    }
                    Object oldGrants = t.getPayload();
                    Object newGrants = refreshed.getPayload();
                    if (newGrants == null) {
                        newGrants = JSONUtil.newObjectNode();
                    }
                    if (newGrants.equals(oldGrants)) {
                        // Grants refreshed, but no change - no need to copy over to all sessions
                        break;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Grants have changed for session: {}, token: {}\nbefore: {}\nafter: {}", t.getSessionId(), mask(t.value()), oldGrants, newGrants);
                    }
                    t.setPayload(newGrants);
                }
            }

        } catch (Throwable t) {
            // Log, but don't rethrow the exception to prevent scheduler cancelling the scheduled job.
            log.error(t.getMessage(), t);
        } finally {
            log.debug("Done refreshing grants");
        }
    }

    private List<SessionFuture> scheduleGrantsRefresh(Predicate<BearerTokenWithPayload> filter, Sessions sessions) {
        List<SessionFuture> scheduled = sessions.executeTask(workerPool, filter, token -> {
            if (log.isTraceEnabled()) {
                log.trace("Fetch grants for session: " + token.getSessionId() + ", token: " + mask(token.value()));
            }

            JsonNode newGrants;
            try {
                newGrants = fetchAuthorizationGrants(token.value());
            } catch (HttpException e) {
                if (403 == e.getStatus()) {
                    // 403 happens when no policy matches the token - thus there are no grants
                    newGrants = JSONUtil.newObjectNode();
                } else {
                    throw e;
                }
            }
            Object oldGrants = token.getPayload();
            if (!newGrants.equals(oldGrants)) {
                if (log.isDebugEnabled()) {
                    log.debug("Grants have changed for session: {}, token: {}\nbefore: {}\nafter: {}", token.getSessionId(), mask(token.value()), oldGrants, newGrants);
                }
                token.setPayload(newGrants);
            }
        });
        return scheduled;
    }

    @Override
    public void addAcls(Set<kafka.security.auth.Acl> acls, kafka.security.auth.Resource resource) {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        super.addAcls(acls, resource);
    }

    @Override
    public boolean removeAcls(Set<kafka.security.auth.Acl> aclsTobeRemoved, kafka.security.auth.Resource resource) {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        return super.removeAcls(aclsTobeRemoved, resource);
    }

    @Override
    public boolean removeAcls(kafka.security.auth.Resource resource) {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        return super.removeAcls(resource);
    }

    @Override
    public Set<kafka.security.auth.Acl> getAcls(kafka.security.auth.Resource resource) {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        return super.getAcls(resource);
    }

    @Override
    public scala.collection.immutable.Map<kafka.security.auth.Resource, Set<kafka.security.auth.Acl>> getAcls(KafkaPrincipal principal) {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        return super.getAcls(principal);
    }

    @Override
    public scala.collection.immutable.Map<kafka.security.auth.Resource, Set<kafka.security.auth.Acl>> getAcls() {
        if (!delegateToKafkaACL) {
            throw new RuntimeException("Simple ACL delegation not enabled");
        }
        return super.getAcls();
    }

    @Override
    public void close() {
        // We don't care about finishing the refresh tasks
        try {
            if (workerPool != null) {
                workerPool.shutdownNow();
            }
        } catch (Exception e) {
            log.error("Failed to shutdown the worker pool", e);
        }
        super.close();
    }
}
