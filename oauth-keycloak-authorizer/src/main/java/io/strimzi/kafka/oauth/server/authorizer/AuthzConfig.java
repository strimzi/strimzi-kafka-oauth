/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

public class AuthzConfig extends Config {

    public static final String STRIMZI_AUTHORIZATION_CLIENT_ID = "strimzi.authorization.client.id";
    public static final String STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI = "strimzi.authorization.token.endpoint.uri";

    public static final String STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME = "strimzi.authorization.kafka.cluster.name";
    public static final String STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL = "strimzi.authorization.delegate.to.kafka.acl";

    public static final String STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS = "strimzi.authorization.grants.refresh.period.seconds";
    public static final String STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE = "strimzi.authorization.grants.refresh.pool.size";

    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION = "strimzi.authorization.ssl.truststore.location";
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD = "strimzi.authorization.ssl.truststore.password";
    public static final String STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE = "strimzi.authorization.ssl.truststore.type";
    public static final String STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION = "strimzi.authorization.ssl.secure.random.implementation";
    public static final String STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM = "strimzi.authorization.ssl.endpoint.identification.algorithm";

    AuthzConfig() {}

    AuthzConfig(Properties p) {
        super(p);
    }
}
