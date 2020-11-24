/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain;

import io.strimzi.kafka.oauth.common.Config;

import java.util.Properties;

public class ServerPlainConfig extends Config {

    public static final String OAUTH_TOKEN_ENDPOINT_URI = "oauth.token.endpoint.uri";

    public ServerPlainConfig() {
    }

    public ServerPlainConfig(Properties p) {
        super(p);
    }
}
