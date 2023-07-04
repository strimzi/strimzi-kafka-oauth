/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.metrics;

import io.strimzi.kafka.oauth.common.Config;

/**
 * Configuration that can be specified as ENV vars, System properties or in <code>server.properties</code> configuration file,
 * but not as part of the JAAS configuration.
 */
public class GlobalConfig extends Config {

    /** The name of the 'strimzi.oauth.metric.reporters' config option */
    public static final String STRIMZI_OAUTH_METRIC_REPORTERS = "strimzi.oauth.metric.reporters";
}
