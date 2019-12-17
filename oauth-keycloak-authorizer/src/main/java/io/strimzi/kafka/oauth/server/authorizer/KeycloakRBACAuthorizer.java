/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.SSLUtil;
import kafka.network.RequestChannel;
import kafka.security.auth.Acl;
import kafka.security.auth.Operation;
import kafka.security.auth.Resource;
import kafka.security.auth.SimpleAclAuthorizer;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.common.HttpUtil.post;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.urlencode;

/**
 * An authorizer that grants access based on security policies managed in Keycloak Authorization Services.
 * It works in conjunction with JaasServerOauthValidatorCallbackHandler, and requires
 * {@link io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder} to be configured as
 * 'principal.builder.class' in 'server.properties' file.
 * <p>
 * To install this authorizer in Kafka, specify the following in your 'server.properties':
 * </p>
 * <pre>
 *     authorizer.class.name=io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer
 *     principal.builder.class=io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder
 * </pre>
 * <p>
 * There is additional configuration that needs to be specified in order for this authorizer to work.
 * </p>
 * <blockquote>
 * Note: The following configuration keys can be specified as properties in Kafka `server.properties` file, or as
 * ENV vars in which case an all-uppercase key name is also attempted with '.' replaced by '_' (e.g. STRIMZI_AUTHZ_TOKEN_ENDPOINT_URI).
 * They can also be specified as system properties. The priority is in reverse - system property overrides the ENV var, which overrides
 * `server.properties`.
 * </blockquote>
 * <p>
 * Required configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authz.token.endpoint.uri</em> A URL of the Keycloak's token endpoint (e.g. https://keycloak:8443/auth/realms/master/protocol/openid-connect/token).<br>
 * If not present, <em>oauth.token.endpoint.uri</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authz.client.id</em> A client id of the OAuth client definition in Keycloak, that has Authorization Services enabled.<br>
 * Typically it is called 'kafka'.
 * If not present, <em>oauth.client.id</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * </ul>
 * <p>
 * Optional configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authz.kafka.cluster.name</em> The name of this cluster, used to target permissions to specific Kafka cluster, making it possible to manage multiple clusters within the same Keycloak realm.<br>
 * The default value is <em>kafka-cluster</em>
 * </li>
 * <li><em>strimzi.authz.delegate.to.kafka.acl</em> Whether authorization decision should be delegated to SimpleACLAuthorizer if DENIED by Keycloak Authorization Services policies.<br>
 * The default value is <em>false</em>
 * </li>
 * </ul>
 * <p>
 * TLS configuration:
 * </p>
 * <ul>
 * <li><em>strimzi.authz.ssl.truststore.location</em> The location of the truststore file on the filesystem.<br>
 * If not present, <em>oauth.ssl.truststore.location</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authz.ssl.truststore.password</em> The password for the truststore.<br>
 * If not present, <em>oauth.ssl.truststore.password</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * </li>
 * <li><em>strimzi.authz.ssl.truststore.type</em> The truststore type.<br>
 * If not present, <em>oauth.ssl.truststore.type</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the <a href="https://docs.oracle.com/javase/8/docs/api/java/security/KeyStore.html#getDefaultType--">Java KeyStore default type</a> is used.
 * </li>
 * <li><em>strimzi.authz.ssl.secure.random.implementation</em> The random number generator implementation. See <a href="https://docs.oracle.com/javase/8/docs/api/java/security/SecureRandom.html#getInstance-java.lang.String-">Java SDK documentation</a>.<br>
 * If not present, <em>oauth.ssl.secure.random.implementation</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the Java platform SDK default is used.
 * </li>
 * <li><em>strimzi.authz.ssl.endpoint.identification.algorithm</em> Specify how to perform hostname verification. If set to empty string the hostname verification is turned off.<br>
 * If not present, <em>oauth.ssl.endpoint.identification.algorithm</em> is used as a fallback configuration key to avoid unnecessary duplication when already present for the purpose of client authentication.
 * If not set, the default value is <em>HTTPS</em> which enforces hostname verification for server certificates.
 * </li>
 * </ul>
 * <p>
 * This authorizer honors the <em>super.users</em> configuration. Super users are automatically granted any authorization request.
 * </p>
 */
public class KeycloakRBACAuthorizer extends SimpleAclAuthorizer {

    static final Logger log = LoggerFactory.getLogger(KeycloakRBACAuthorizer.class);

    private URI tokenEndpointUrl;
    private String clientId;
    private String clusterName;
    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;
    private List<UserSpec> superUsers = Collections.emptyList();
    private boolean delegateToKafkaACL = false;


    public KeycloakRBACAuthorizer() {
        super();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        super.configure(configs);

        AuthzConfig config = convertToCommonConfig(configs);

        String endpoint = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHZ_TOKEN_ENDPOINT_URI,
                ClientConfig.OAUTH_TOKEN_ENDPOINT_URI);
        if (endpoint == null) {
            throw new RuntimeException("OAuth2 Token Endpoint ('strimzi.authz.token.endpoint.uri') not set.");
        }

        try {
            tokenEndpointUrl = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Specified token endpoint uri is invalid: " + endpoint);
        }

        clientId = ConfigUtil.getConfigWithFallbackLookup(config, AuthzConfig.STRIMZI_AUTHZ_CLIENT_ID, ClientConfig.OAUTH_CLIENT_ID);
        if (clientId == null) {
            throw new RuntimeException("OAuth2 Client Id ('strimzi.authz.client.id') not set.");
        }

        socketFactory = createSSLFactory(config);
        hostnameVerifier = createHostnameVerifier(config);

        clusterName = config.getValue(AuthzConfig.STRIMZI_AUTHZ_KAFKA_CLUSTER_NAME);
        if (clusterName == null) {
            clusterName = "kafka-cluster";
        }

        delegateToKafkaACL = config.getValueAsBoolean(AuthzConfig.STRIMZI_AUTHZ_DELEGATE_TO_KAFKA_ACL, false);

        String users = (String) configs.get("super.users");
        if (users != null) {
            superUsers = Arrays.asList(users.split(";"))
                    .stream()
                    .map(s -> UserSpec.of(s))
                    .collect(Collectors.toList());
        }
    }

    /**
     * This method transforms strimzi.authz.* entries into oauth.* entries in order to be able to use existing ConfigUtil
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

        String[] keys = {
            AuthzConfig.STRIMZI_AUTHZ_DELEGATE_TO_KAFKA_ACL,
            AuthzConfig.STRIMZI_AUTHZ_KAFKA_CLUSTER_NAME,
            AuthzConfig.STRIMZI_AUTHZ_CLIENT_ID,
            AuthzConfig.OAUTH_CLIENT_ID,
            AuthzConfig.STRIMZI_AUTHZ_TOKEN_ENDPOINT_URI,
            ClientConfig.OAUTH_TOKEN_ENDPOINT_URI,
            AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_LOCATION,
            Config.OAUTH_SSL_TRUSTSTORE_LOCATION,
            AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_PASSWORD,
            Config.OAUTH_SSL_TRUSTSTORE_PASSWORD,
            AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_TYPE,
            Config.OAUTH_SSL_TRUSTSTORE_TYPE,
            AuthzConfig.STRIMZI_AUTHZ_SSL_SECURE_RANDOM_IMPLEMENTATION,
            Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION,
            AuthzConfig.STRIMZI_AUTHZ_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM
        };

        // copy over the keys
        for (String key: keys) {
            ConfigUtil.putIfNotNull(p, key, configs.get(key));
        }

        return new AuthzConfig(p);
    }

    static SSLSocketFactory createSSLFactory(Config config) {
        String truststore = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_LOCATION, Config.OAUTH_SSL_TRUSTSTORE_LOCATION);
        String password = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_PASSWORD, Config.OAUTH_SSL_TRUSTSTORE_PASSWORD);
        String type = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHZ_SSL_TRUSTSTORE_TYPE, Config.OAUTH_SSL_TRUSTSTORE_TYPE);
        String rnd = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHZ_SSL_SECURE_RANDOM_IMPLEMENTATION, Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);

        return SSLUtil.createSSLFactory(truststore, password, type, rnd);
    }

    static HostnameVerifier createHostnameVerifier(Config config) {
        String hostCheck = ConfigUtil.getConfigWithFallbackLookup(config,
                AuthzConfig.STRIMZI_AUTHZ_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM);

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
    public boolean authorize(RequestChannel.Session session, Operation operation, Resource resource) {

        if (!(session.principal() instanceof JwtKafkaPrincipal)) {
            throw new IllegalStateException("Kafka Broker is misconfigured. KeycloakRBACAuthorizer requires io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder as 'principal.builder.class'");
        }

        JwtKafkaPrincipal principal = (JwtKafkaPrincipal) session.principal();
        for (UserSpec u: superUsers) {
            if (principal.getPrincipalType().equals(u.getType())
                    && principal.getName().equals(u.getName())) {

                // it's a super user. super users are granted everything
                if (log.isDebugEnabled()) {
                    log.debug("Authorization GRANTED - user is a superuser: " + session.principal() + ", operation: " + operation + ", resource: " + resource);
                }
                return true;
            }
        }

        //
        // Check if authorization grants are available
        // If not, fetch authorization grants and store them in the token
        //

        BearerTokenWithPayload token = principal.getJwt();
        JsonNode authz = (JsonNode) token.getPayload();

        if (authz == null) {
            // fetch authorization grants
            try {
                authz = fetchAuthorizationGrants(token.value());
                if (authz == null) {
                    authz = new ObjectNode(JSONUtil.MAPPER.getNodeFactory());
                }
            } catch (HttpException e) {
                if (e.getStatus() == 403) {
                    authz = new ObjectNode(JSONUtil.MAPPER.getNodeFactory());
                } else {
                    log.warn("Unexpected status while fetching authorization data - will retry next time: " + e.getMessage());
                }
            }
            if (authz != null) {
                // store authz grants in the token so they are available for subsequent requests
                token.setPayload(authz);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("authorize(): " + authz);
        }

        //
        // Iterate authorization rules and try to find a match
        //

        Iterator<JsonNode> it = authz.iterator();
        while (it.hasNext()) {
            JsonNode permission = it.next();
            String name = permission.get("rsname").asText();
            ResourceSpec resourceSpec = ResourceSpec.of(name);
            if (resourceSpec.match(clusterName, resource.resourceType().name(), resource.name())) {

                ScopesSpec grantedScopes = ScopesSpec.of(
                        validateScopes(
                            JSONUtil.asListOfString(permission.get("scopes"))));

                if (grantedScopes.isGranted(operation.name())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Authorization GRANTED - cluster: " + clusterName + ",user: " + session.principal() + ", operation: " + operation +
                                ", resource: " + resource + "\nGranted scopes for resource (" + resourceSpec + "): " + grantedScopes);
                    }
                    return true;
                }
            }
        }

        return delegateIfRequested(session, operation, resource, authz);
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

    boolean delegateIfRequested(RequestChannel.Session session, Operation operation, Resource resource, JsonNode authz) {
        if (delegateToKafkaACL) {
            boolean granted = super.authorize(session, operation, resource);

            if (log.isDebugEnabled()) {
                String status = granted ? "GRANTED" : "DENIED";
                log.debug("Authorization " + status + " by ACL - user: " + session.principal() + ", operation: " + operation + ", resource: " + resource);
            }
            return granted;
        }

        if (log.isDebugEnabled()) {
            log.debug("Authorization DENIED - cluster: " + clusterName + ", user: " + session.principal() + ", operation: " + operation + ", resource: " + resource + "\n permissions: " + authz);
        }
        return false;
    }

    JsonNode fetchAuthorizationGrants(String token) {

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

    @Override
    public void addAcls(Set<Acl> acls, Resource resource) {
        super.addAcls(acls, resource);
    }

    @Override
    public boolean removeAcls(Set<Acl> aclsTobeRemoved, Resource resource) {
        return super.removeAcls(aclsTobeRemoved, resource);
    }

    @Override
    public boolean removeAcls(Resource resource) {
        return super.removeAcls(resource);
    }

    @Override
    public Set<Acl> getAcls(Resource resource) {
        return super.getAcls(resource);
    }

    @Override
    public scala.collection.immutable.Map<Resource, Set<Acl>> getAcls(KafkaPrincipal principal) {
        return super.getAcls(principal);
    }

    @Override
    public scala.collection.immutable.Map<Resource, Set<Acl>> getAcls() {
        return super.getAcls();
    }

    @Override
    public void close() {
        super.close();
    }
}
