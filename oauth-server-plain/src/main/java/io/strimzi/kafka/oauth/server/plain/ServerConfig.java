/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

public class ServerConfig extends Config {

    public static final String OAUTH_JWKS_ENDPOINT_URI = "oauth.jwks.endpoint.uri";
    public static final String OAUTH_JWKS_EXPIRY_SECONDS = "oauth.jwks.expiry.seconds";
    public static final String OAUTH_JWKS_REFRESH_SECONDS = "oauth.jwks.refresh.seconds";
    public static final String OAUTH_JWKS_REFRESH_MIN_PAUSE_SECONDS = "oauth.jwks.refresh.min.pause.seconds";
    public static final String OAUTH_VALID_ISSUER_URI = "oauth.valid.issuer.uri";
    public static final String OAUTH_INTROSPECTION_ENDPOINT_URI = "oauth.introspection.endpoint.uri";
    public static final String OAUTH_USERINFO_ENDPOINT_URI = "oauth.userinfo.endpoint.uri";
    public static final String OAUTH_CHECK_ACCESS_TOKEN_TYPE = "oauth.check.access.token.type";
    public static final String OAUTH_CHECK_ISSUER = "oauth.check.issuer";
    public static final String OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE = "oauth.crypto.provider.bouncycastle";
    public static final String OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE_POSITION = "oauth.crypto.provider.bouncycastle.position";
    public static final String OAUTH_VALID_TOKEN_TYPE = "oauth.valid.token.type";

    public static final String STRIMZI_AUTHORIZER_DELEGATE_CLASS_NAME = "strimzi.authorizer.delegate.class.name";
    public static final String STRIMZI_AUTHORIZER_GRANT_WHEN_NO_DELEGATE = "strimzi.authorizer.grant.when.no.delegate";

    @Deprecated
    public static final String OAUTH_VALIDATION_SKIP_TYPE_CHECK = "oauth.validation.skip.type.check";

    public ServerConfig() {
    }

    public ServerConfig(Properties p) {
        super(p);
    }
}
