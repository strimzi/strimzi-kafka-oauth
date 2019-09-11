/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

public class ServerConfig extends Config {

    public static final String OAUTH_JWKS_ENDPOINT_URI = "oauth.jwks.endpoint.uri";
    public static final String OAUTH_JWKS_EXPIRY_SECONDS = "oauth.jwks.expiry.seconds";
    public static final String OAUTH_JWKS_REFRESH_SECONDS = "oauth.jwks.refresh.seconds";
    public static final String OAUTH_VALID_ISSUER_URI = "oauth.valid.issuer.uri";
    public static final String OAUTH_VALIDATE_COMMON_CHECKS = "oauth.validate.common.checks";
    public static final String OAUTH_VALIDATE_AUDIENCE = "oauth.validate.audience";
    public static final String OAUTH_INTROSPECTION_ENDPOINT_URI = "oauth.introspection.endpoint.uri";

    public ServerConfig() {
    }

    public ServerConfig(Properties p) {
        super(p);
    }
}
