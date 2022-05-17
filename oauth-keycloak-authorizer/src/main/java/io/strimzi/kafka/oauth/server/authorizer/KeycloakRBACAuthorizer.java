/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.ConfigUtil;
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
import io.strimzi.kafka.oauth.services.SessionFuture;
import io.strimzi.kafka.oauth.services.Sessions;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import io.strimzi.kafka.oauth.validator.DaemonThreadFactory;
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
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
 * <li><em>strimzi.authorization.delegate.to.kafka.acl</em> Whether authorization decision should be delegated to ACLAuthorizer if DENIED by Keycloak Authorization Services policies.<br>
 * The default value is <em>false</em>
 * </li>
 * <li><em>strimzi.authorization.grants.refresh.period.seconds</em> The time interval for refreshing the grants of the active sessions. The scheduled job iterates over active sessions and fetches a fresh list of grants for each.<br>
 * The default value is <em>60</em>
 * </li>
 * <li><em>strimzi.authorization.grants.refresh.pool.size</em> The number of threads to fetch grants from token endpoint (in parallel).<br>
 * The default value is <em>5</em>
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
@SuppressWarnings("deprecation")
public class KeycloakRBACAuthorizer extends AclAuthorizer {

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
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;

    // Turning it to false will not enforce access token expiry time (only for debugging purposes during development)
    private final boolean denyWhenTokenInvalid = true;

    private ExecutorService workerPool;

    private OAuthMetrics metrics;
    private boolean enableMetrics;
    private SensorKeyProducer authzSensorKeyProducer;
    private SensorKeyProducer grantsSensorKeyProducer;

    public KeycloakRBACAuthorizer() {
        super();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        super.configure(configs);

        AuthzConfig config = convertToCommonConfig(configs);

        String pbclass = (String) configs.get("principal.builder.class");
        if (!PRINCIPAL_BUILDER_CLASS.equals(pbclass) && !DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            throw new ConfigException("KeycloakRBACAuthorizer requires " + PRINCIPAL_BUILDER_CLASS + " as 'principal.builder.class'");
        }

        if (DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            log.warn("The '" + DEPRECATED_PRINCIPAL_BUILDER_CLASS + "' class has been deprecated, and may be removed in the future. Please use '" + PRINCIPAL_BUILDER_CLASS + "' as 'principal.builder.class' instead.");
        }

        String endpoint = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
                ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);
        if (endpoint == null) {
            throw new ConfigException("OAuth2 Token Endpoint ('strimzi.authorization.token.endpoint.uri') not set.");
        }

        try {
            tokenEndpointUrl = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new ConfigException("Specified token endpoint uri is invalid: " + endpoint);
        }

        clientId = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_CLIENT_ID, ClientConfig.OAUTH_CLIENT_ID);
        if (clientId == null) {
            throw new ConfigException("OAuth2 Client Id ('strimzi.authorization.client.id') not set.");
        }

        connectTimeoutSeconds = ConfigUtil.getTimeoutConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS, ClientConfig.OAUTH_CONNECT_TIMEOUT_SECONDS);
        readTimeoutSeconds = ConfigUtil.getTimeoutConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS, ClientConfig.OAUTH_READ_TIMEOUT_SECONDS);

        socketFactory = createSSLFactory(config);
        hostnameVerifier = createHostnameVerifier(config);

        clusterName = config.getValue(AuthzConfig.STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME);
        if (clusterName == null) {
            clusterName = "kafka-cluster";
        }

        delegateToKafkaACL = config.getValueAsBoolean(AuthzConfig.STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL, false);

        String users = (String) configs.get("super.users");
        if (users != null) {
            superUsers = Arrays.stream(users.split(";"))
                    .map(UserSpec::of)
                    .collect(Collectors.toList());
        }

        // Number of threads that can perform token endpoint requests at the same time
        final int grantsRefreshPoolSize = config.getValueAsInt(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE, 5);
        if (grantsRefreshPoolSize < 1) {
            throw new ConfigException("Invalid value of 'strimzi.authorization.grants.refresh.pool.size': " + grantsRefreshPoolSize + ". Has to be >= 1.");
        }

        // Less or equal zero means to never check
        final int grantsRefreshPeriodSeconds = config.getValueAsInt(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, 60);

        if (grantsRefreshPeriodSeconds > 0) {
            workerPool = Executors.newFixedThreadPool(grantsRefreshPoolSize);
            setupRefreshGrantsJob(grantsRefreshPeriodSeconds);
        }

        configureMetrics(configs, config);

        authzSensorKeyProducer = new KeycloakAuthorizationSensorKeyProducer("keycloak-authorizer", tokenEndpointUrl);
        grantsSensorKeyProducer = new GrantsHttpSensorKeyProducer("keycloak-authorizer", tokenEndpointUrl);

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
                    + "\n    connectTimeoutSeconds: " + connectTimeoutSeconds
                    + "\n    readTimeoutSeconds: " + readTimeoutSeconds
                    + "\n    enableMetrics: " + enableMetrics
            );
        }
    }

    private void configureMetrics(Map<String, ?> configs, AuthzConfig config) {
        if (!Services.isAvailable()) {
            Services.configure(configs);
        }

        enableMetrics = config.getValueAsBoolean(Config.OAUTH_ENABLE_METRICS, false);
        if (enableMetrics) {
            metrics = Services.getInstance().getMetrics();
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
            Config.OAUTH_CLIENT_ID,
            AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
            ClientConfig.OAUTH_TOKEN_ENDPOINT_URI,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION,
            Config.OAUTH_SSL_TRUSTSTORE_LOCATION,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES,
            Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD,
            Config.OAUTH_SSL_TRUSTSTORE_PASSWORD,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE,
            Config.OAUTH_SSL_TRUSTSTORE_TYPE,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION,
            Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION,
            AuthzConfig.STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            AuthzConfig.STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS,
            Config.OAUTH_CONNECT_TIMEOUT_SECONDS,
            AuthzConfig.STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS,
            Config.OAUTH_READ_TIMEOUT_SECONDS,
            Config.OAUTH_ENABLE_METRICS
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
        String truststoreData = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES, Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES);
        String password = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD, Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE, Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION, Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, truststoreData, password, type, rnd);
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
     * @param requestContext Request context including request type, security protocol and listener name
     * @param actions Actions being authorized including resource and operation for each action
     * @return List of authorization results for each action in the same order as the provided actions
     */
    public List<AuthorizationResult> authorize(AuthorizableRequestContext requestContext, List<Action> actions) {

        JsonNode grants = null;
        long startTime = System.currentTimeMillis();
        List<AuthorizationResult> result;

        try {
            KafkaPrincipal principal = requestContext.principal();

            for (UserSpec u : superUsers) {
                if (principal.getPrincipalType().equals(u.getType())
                        && principal.getName().equals(u.getName())) {

                    for (Action action: actions) {
                        // It's a super user. super users are granted everything
                        if (GRANT_LOG.isDebugEnabled() && action.logIfAllowed()) {
                            GRANT_LOG.debug("Authorization GRANTED - user is a superuser: " + requestContext.principal() +
                                    ", cluster: " + clusterName + ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern()));
                        }
                    }
                    addAuthzMetricSuccessTime(startTime);
                    return Collections.nCopies(actions.size(), AuthorizationResult.ALLOWED);
                }
            }

            if (!(principal instanceof OAuthKafkaPrincipal)) {
                // If user wasn't authenticated over OAuth, and simple ACL delegation is enabled
                // we delegate to simple ACL
                result = delegateIfRequested(requestContext, actions, null);

                addAuthzMetricSuccessTime(startTime);
                return result;
            }

            //
            // Check if authorization grants are available
            // If not, fetch authorization grants and store them in the token
            //

            OAuthKafkaPrincipal jwtPrincipal = (OAuthKafkaPrincipal) principal;

            BearerTokenWithPayload token = jwtPrincipal.getJwt();

            if (denyIfTokenInvalid(token)) {
                addAuthzMetricSuccessTime(startTime);
                return Collections.nCopies(actions.size(), AuthorizationResult.DENIED);
            }

            grants = (JsonNode) token.getPayload();

            if (grants == null) {
                grants = handleFetchingGrants(token);
            }

            if (log.isDebugEnabled()) {
                log.debug("Authorization grants for user {}: {}", principal, grants);
            }

            if (grants != null) {
                result = allowOrDenyBasedOnGrants(requestContext, actions, grants);
            } else {
                result = delegateIfRequested(requestContext, actions, null);
            }
            addAuthzMetricSuccessTime(startTime);
            return result;

        } catch (Throwable t) {
            log.error("An unexpected exception has occurred: ", t);
            if (DENY_LOG.isDebugEnabled()) {
                DENY_LOG.debug("Authorization DENIED due to error - user: " + requestContext.principal() +
                        ", cluster: " + clusterName + ", actions: " + actions + ",\n permissions: " + grants);
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


    private List<AuthorizationResult> allowOrDenyBasedOnGrants(AuthorizableRequestContext requestContext, List<Action> actions, JsonNode grants) {
        List<AuthorizationResult> results = new ArrayList<>(actions.size());

        //
        // Iterate authorization rules and try to find a match
        //
        action_loop:
        for (Action action: actions) {
            for (JsonNode permission : grants) {
                String name = permission.get("rsname").asText();
                ResourceSpec resourceSpec = ResourceSpec.of(name);
                if (resourceSpec.match(clusterName, action.resourcePattern().resourceType().name(), action.resourcePattern().name())) {

                    JsonNode scopes = permission.get("scopes");
                    ScopesSpec grantedScopes = scopes == null ? null : ScopesSpec.of(
                            validateScopes(
                                    JSONUtil.asListOfString(scopes)));

                    if (scopes == null || grantedScopes.isGranted(action.operation().name())) {
                        if (GRANT_LOG.isDebugEnabled() && action.logIfAllowed()) {
                            GRANT_LOG.debug("Authorization GRANTED - cluster: " + clusterName + ", user: " + requestContext.principal() +
                                    ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern()) +
                                    "\nGranted scopes for resource (" + resourceSpec + "): " + (grantedScopes == null ? "ALL" : grantedScopes));
                        }
                        results.add(AuthorizationResult.ALLOWED);
                        continue action_loop;
                    }
                }
            }
            results.addAll(delegateIfRequested(requestContext, Collections.singletonList(action), grants));
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
                enumScopes.add(ScopesSpec.AuthzScope.of(name));
            } catch (Exception e) {
                log.warn("[IGNORED] Invalid scope detected in authorization scopes list: " + name);
            }
        }
        return enumScopes;
    }

    private List<AuthorizationResult> delegateIfRequested(AuthorizableRequestContext context, List<Action> actions, JsonNode authz) {
        String nonAuthMessageFragment = context.principal() instanceof OAuthKafkaPrincipal ? "" : " non-oauth";
        if (delegateToKafkaACL) {
            List<AuthorizationResult> results = super.authorize(context, actions);

            int i = 0;
            for (AuthorizationResult result: results) {
                Action action = actions.get(i);
                boolean grantLogOn = result == AuthorizationResult.ALLOWED && GRANT_LOG.isDebugEnabled() && action.logIfAllowed();
                boolean denyLogOn = result == AuthorizationResult.DENIED && DENY_LOG.isDebugEnabled() && action.logIfDenied();

                if (grantLogOn || denyLogOn) {
                    String status = result == AuthorizationResult.ALLOWED ? "GRANTED" : "DENIED";
                    String message = "Authorization " + status + " by ACL -" + nonAuthMessageFragment + " user: " + context.principal() +
                            ", operation: " + action.operation() + ", resource: " + fromResourcePattern(action.resourcePattern());

                    if (grantLogOn) {
                        GRANT_LOG.debug(message);
                    } else {
                        DENY_LOG.debug(message);
                    }
                }
                i++;
            }
            return results;
        }

        if (DENY_LOG.isDebugEnabled()) {
            for (Action action: actions) {
                if (action.logIfDenied()) {
                    DENY_LOG.debug("Authorization DENIED -" + nonAuthMessageFragment + " user: " + context.principal() +
                            ", cluster: " + clusterName + ", operation: " + action.operation() +
                            ", resource: " + fromResourcePattern(action.resourcePattern()) + ",\n permissions: " + authz);
                }
            }
        }
        return Collections.nCopies(actions.size(), AuthorizationResult.DENIED);
    }

    private JsonNode fetchAuthorizationGrants(String token) {

        String authorization = "Bearer " + token;

        StringBuilder body = new StringBuilder("audience=").append(urlencode(clientId))
                .append("&grant_type=").append(urlencode("urn:ietf:params:oauth:grant-type:uma-ticket"))
                .append("&response_mode=permissions");

        JsonNode response;
        long startTime = System.currentTimeMillis();

        try {
            response = post(tokenEndpointUrl, socketFactory, hostnameVerifier, authorization,
                    "application/x-www-form-urlencoded", body.toString(), JsonNode.class, connectTimeoutSeconds, readTimeoutSeconds);
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

    private void setupRefreshGrantsJob(int refreshSeconds) {
        // Set up periodic timer to fetch grants for active sessions every refresh seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

        scheduler.scheduleAtFixedRate(this::refreshGrants, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
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
            List<SessionFuture<?>> scheduled = scheduleGrantsRefresh(filter, sessions);

            for (SessionFuture<?> f: scheduled) {
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

    private List<SessionFuture<?>> scheduleGrantsRefresh(Predicate<BearerTokenWithPayload> filter, Sessions sessions) {
        return sessions.executeTask(workerPool, filter, token -> {
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

    @Override
    public java.util.Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo serverInfo) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (!delegateToKafkaACL) {
            return serverInfo.endpoints().stream().collect(Collectors.toMap(Function.identity(), e -> future));
        }
        return super.start(serverInfo);
    }

    @Override
    public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext requestContext, List<AclBinding> aclBindings) {
        if (!delegateToKafkaACL) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return super.createAcls(requestContext, aclBindings);
    }

    @Override
    public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext requestContext, List<AclBindingFilter> aclBindingFilters) {
        if (!delegateToKafkaACL) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return super.deleteAcls(requestContext, aclBindingFilters);
    }

    @Override
    public Iterable<AclBinding> acls(AclBindingFilter filter) {
        if (!delegateToKafkaACL) {
            throw new UnsupportedOperationException("Simple ACL delegation not enabled");
        }
        return super.acls(filter);
    }

    private void addAuthzMetricSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authzSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addAuthzMetricErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(authzSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addGrantsHttpMetricSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(grantsSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addGrantsHttpMetricErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(grantsSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }

}
