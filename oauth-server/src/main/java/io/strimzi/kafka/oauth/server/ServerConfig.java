/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

/**
 * Configuration handling class used in server callback handlers
 */
public class ServerConfig extends Config {

    /**
     * "oauth.jwks.endpoint.uri"
     */
    public static final String OAUTH_JWKS_ENDPOINT_URI = "oauth.jwks.endpoint.uri";

    /**
     * "oauth.jwks.expiry.seconds"
     */
    public static final String OAUTH_JWKS_EXPIRY_SECONDS = "oauth.jwks.expiry.seconds";

    /**
     * "oauth.jwks.refresh.seconds"
     */
    public static final String OAUTH_JWKS_REFRESH_SECONDS = "oauth.jwks.refresh.seconds";

    /**
     * "oauth.jwks.refresh.min.pause.seconds"
     */
    public static final String OAUTH_JWKS_REFRESH_MIN_PAUSE_SECONDS = "oauth.jwks.refresh.min.pause.seconds";

    /**
     * "oauth.jwks.ignore.key.use"
     */
    public static final String OAUTH_JWKS_IGNORE_KEY_USE = "oauth.jwks.ignore.key.use";

    /**
     * "oauth.valid.issuer.uri"
     */
    public static final String OAUTH_VALID_ISSUER_URI = "oauth.valid.issuer.uri";

    /**
     * "oauth.introspection.endpoint.uri"
     */
    public static final String OAUTH_INTROSPECTION_ENDPOINT_URI = "oauth.introspection.endpoint.uri";

    /**
     * "oauth.userinfo.endpoint.uri"
     */
    public static final String OAUTH_USERINFO_ENDPOINT_URI = "oauth.userinfo.endpoint.uri";

    /**
     * "oauth.check.access.token.type"
     */
    public static final String OAUTH_CHECK_ACCESS_TOKEN_TYPE = "oauth.check.access.token.type";

    /**
     * "oauth.check.issuer"
     */
    public static final String OAUTH_CHECK_ISSUER = "oauth.check.issuer";

    /**
     * "oauth.check.audience"
     */
    public static final String OAUTH_CHECK_AUDIENCE = "oauth.check.audience";

    /**
     * "oauth.custom.claim.check"
     */
    public static final String OAUTH_CUSTOM_CLAIM_CHECK = "oauth.custom.claim.check";

    /**
     * "oauth.groups.claim"
     */
    public static final String OAUTH_GROUPS_CLAIM = "oauth.groups.claim";

    /**
     * "oauth.groups.claim.delimiter"
     */
    public static final String OAUTH_GROUPS_CLAIM_DELIMITER = "oauth.groups.claim.delimiter";

    /**
     * "oauth.include.accept.header"
     */
    public static final String OAUTH_INCLUDE_ACCEPT_HEADER = "oauth.include.accept.header";

    /**
     * "oauth.valid.token.type"
     */
    public static final String OAUTH_VALID_TOKEN_TYPE = "oauth.valid.token.type";

    /**
     * "oauth.fail.fast"
     */
    public static final String OAUTH_FAIL_FAST = "oauth.fail.fast";

    /**
     * "strimzi.authorizer.delegate.class.name"
     */
    public static final String STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME = "strimzi.authorizer.delegate.class.name";

    /**
     * "strimzi.authorizer.grant.when.no.delegate"
     */
    public static final String STRIMZI_AUTHORIZER_GRANT_WHEN_NO_DELEGATE = "strimzi.authorizer.grant.when.no.delegate";

    /**
     * "oauth.validation.skip.type.check"
     */
    @Deprecated
    public static final String OAUTH_VALIDATION_SKIP_TYPE_CHECK = "oauth.validation.skip.type.check";

    /**
     * Create a new instance
     */
    public ServerConfig() {
    }

    /**
     * Create a new instance
     *
     * @param p Config properties
     */
    public ServerConfig(Properties p) {
        super(p);
    }
}
