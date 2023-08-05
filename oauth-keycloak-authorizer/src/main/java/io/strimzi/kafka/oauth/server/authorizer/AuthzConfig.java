/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

/**
 * Configuration handling class used in {@link KeycloakRBACAuthorizer}
 */
public class AuthzConfig extends Config {

    /**
     * Client id used by authorizer when requesting grants from Keycloak Authorization Services.
     */
    public static final String STRIMZI_AUTHORIZATION_CLIENT_ID = "strimzi.authorization.client.id";

    /**
     * Keycloak token endpoint used to fetch grants for individual access token.
     */
    public static final String STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI = "strimzi.authorization.token.endpoint.uri";

    /**
     * The cluster name used by this configuration which can be targeted in Keycloak Authorization Services by a resource name prefix 'cluster-name:$CLUSTER_NAME,'.
     */
    public static final String STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME = "strimzi.authorization.kafka.cluster.name";

    /**
     * If true, the authorization decision is delegated to standard kafka ACL authorizer for non-oauth listeners and whenever
     * the Keycloak Authorization Services grants don't result in ALLOWED permission.
     */
    public static final String STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL = "strimzi.authorization.delegate.to.kafka.acl";

    /**
     * The time period in seconds for the background job to refresh the cached grants for active sessions. That allows changes in permissions to be detected for active sessions .
     */
    public static final String STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS = "strimzi.authorization.grants.refresh.period.seconds";

    /**
     * The number of worker threads used by the background job that refreshes the grants.
     */
    public static final String STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE = "strimzi.authorization.grants.refresh.pool.size";

    /**
     * The maximum time in seconds that a grant is kept in grants cache without being accessed. It allows for active releasing of memory rather than waiting for VM's gc() to kick in.
     */
    public static final String STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS = "strimzi.authorization.grants.max.idle.time.seconds";

    /**
     * A period in seconds for a background service that removes no-longer-used grants information from grants cache.
     */
    public static final String STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS = "strimzi.authorization.grants.gc.period.seconds";

    /**
     * A maximum number of retries to attempt if the request to Keycloak token endpoint fails in unexpected way (connection timeout, read timeout, unexpected HTTP status code, unexpected response body).
     */
    public static final String STRIMZI_AUTHORIZATION_HTTP_RETRIES = "strimzi.authorization.http.retries";

    /**
     * Truststore file location
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION = "strimzi.authorization.ssl.truststore.location";

    /**
     * Trusted certificates in PEM format as alternative way to provide certs
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES = "strimzi.authorization.ssl.truststore.certificates";

    /**
     * Truststore password
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD = "strimzi.authorization.ssl.truststore.password";

    /**
     * Truststore type
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE = "strimzi.authorization.ssl.truststore.type";

    /**
     * Pseudo random number generator implementation to use for HTTPS.
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION = "strimzi.authorization.ssl.secure.random.implementation";

    /**
     * Certificate checking method to use for HTTPS.
     */
    public static final String STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM = "strimzi.authorization.ssl.endpoint.identification.algorithm";

    /**
     * Connect timeout for connections to the token endpoint in seconds.
     */
    public static final String STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS = "strimzi.authorization.connect.timeout.seconds";

    /**
     * Read timeout for connections to the token endpoint in seconds.
     */
    public static final String STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS = "strimzi.authorization.read.timeout.seconds";

    /**
     * Enable authorization specific metrics.
     */
    public static final String STRIMZI_AUTHORIZATION_ENABLE_METRICS = "strimzi.authorization.enable.metrics";

    /**
     * Reuse cached grants for the same principal (user id) possibly fetched by another session using a different access token.
     */
    public static final String STRIMZI_AUTHORIZATION_REUSE_GRANTS = "strimzi.authorization.reuse.grants";

    /**
     * Disable sending the <code>Accept</code> header to the upstream server.
     */
    public static final String STRIMZI_AUTHORIZATION_INCLUDE_ACCEPT_HEADER = "strimzi.authorization.include.accept.header";


    /**
     * Create a new instance
     *
     * @param p Config properties
     */
    AuthzConfig(Properties p) {
        super(p);
    }
}
