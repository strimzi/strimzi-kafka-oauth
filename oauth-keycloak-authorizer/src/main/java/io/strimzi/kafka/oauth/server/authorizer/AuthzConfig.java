/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

public class AuthzConfig extends Config {

    public static final String STRIMZI_AUTHZ_CLIENT_ID = "strimzi.authz.client.id";
    public static final String STRIMZI_AUTHZ_TOKEN_ENDPOINT_URI = "strimzi.authz.token.endpoint.uri";

    public static final String STRIMZI_AUTHZ_KAFKA_CLUSTER_NAME = "strimzi.authz.kafka.cluster.name";
    public static final String STRIMZI_AUTHZ_DELEGATE_TO_KAFKA_ACL = "strimzi.authz.delegate.to.kafka.acl";

    public static final String STRIMZI_AUTHZ_SSL_TRUSTSTORE_LOCATION = "strimzi.authz.ssl.truststore.location";
    public static final String STRIMZI_AUTHZ_SSL_TRUSTSTORE_PASSWORD = "strimzi.authz.ssl.truststore.password";
    public static final String STRIMZI_AUTHZ_SSL_TRUSTSTORE_TYPE = "strimzi.authz.ssl.truststore.type";
    public static final String STRIMZI_AUTHZ_SSL_SECURE_RANDOM_IMPLEMENTATION = "strimzi.authz.ssl.secure.random.implementation";
    public static final String STRIMZI_AUTHZ_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM = "strimzi.authz.ssl.endpoint.identification.algorithm";

    AuthzConfig() {}

    AuthzConfig(Properties p) {
        super(p);
    }
}
