/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nested annotation for configuring the Kafka container within an {@code @OAuthEnvironment}.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaConfig {

    /** The listener configuration preset to use */
    KafkaPreset preset();

    /** Whether to enable Prometheus metrics on the Kafka container */
    boolean metrics() default false;

    /** Whether to auto-initialize MockOAuth endpoints to MODE_200 after Kafka starts */
    boolean initEndpoints() default true;

    /** Whether to auto-setup ACLs after Kafka starts (for KEYCLOAK_AUTHZ preset) */
    boolean setupAcls() default false;
}
