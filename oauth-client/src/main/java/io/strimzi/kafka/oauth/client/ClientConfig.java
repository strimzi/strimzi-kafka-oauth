/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.client;

import io.strimzi.kafka.oauth.common.Config;

/**
 * A {@link Config} object used for client configuration
 */
public class ClientConfig extends Config {

    /**
     * "oauth.access.token"
     */
    public static final String OAUTH_ACCESS_TOKEN = "oauth.access.token";

    /**
     * "oauth.access.token.location"
     */
    public static final String OAUTH_ACCESS_TOKEN_LOCATION = "oauth.access.token.location";

    /**
     * "oauth.refresh.token"
     */
    public static final String OAUTH_REFRESH_TOKEN = "oauth.refresh.token";

    /**
     * "oauth.refresh.token.location"
     */
    public static final String OAUTH_REFRESH_TOKEN_LOCATION = "oauth.refresh.token.location";

    /**
     * "oauth.token.endpoint.uri"
     */
    public static final String OAUTH_TOKEN_ENDPOINT_URI = "oauth.token.endpoint.uri";

    /**
     * "oauth.max.token.expiry.seconds"
     */
    public static final String OAUTH_MAX_TOKEN_EXPIRY_SECONDS = "oauth.max.token.expiry.seconds";

    /**
     * "oauth.password.grant.username"
     */
    public static final String OAUTH_PASSWORD_GRANT_USERNAME = "oauth.password.grant.username";

    /**
     * "oauth.password.grant.password"
     */
    public static final String OAUTH_PASSWORD_GRANT_PASSWORD = "oauth.password.grant.password";

    /**
     * "oauth.client.assertion"
     */
    public static final String OAUTH_CLIENT_ASSERTION = "oauth.client.assertion";

    /**
     * "oauth.client.assertion.location"
     */
    public static final String OAUTH_CLIENT_ASSERTION_LOCATION = "oauth.client.assertion.location";

    /**
     * "oauth.client.assertion.type"
     */
    public static final String OAUTH_CLIENT_ASSERTION_TYPE = "oauth.client.assertion.type";

    /**
     * Create a new instance
     */
    public ClientConfig() {
    }

    /**
     * Create a new instance
     *
     * @param p Properties object containing the configuration
     */
    public ClientConfig(java.util.Properties p) {
        super(p);
    }
}
