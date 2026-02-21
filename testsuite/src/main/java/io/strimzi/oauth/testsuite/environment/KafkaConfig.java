/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.test.container.AuthenticationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for configuring the Kafka container in an OAuth test.
 *
 * <p>Can be used in two ways:
 * <ul>
 *   <li>As a member of {@code @OAuthEnvironment} to set the class-level default Kafka config</li>
 *   <li>Directly on a test method to override the class-level config for that test only</li>
 * </ul>
 *
 * <p>When used on a method, the Kafka cluster is restarted with the method's config before the test
 * runs, and restored to the class-level config for the next test (if different).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaConfig {

    /** Authentication type for the Kafka listener */
    AuthenticationType authenticationType() default AuthenticationType.OAUTH_BEARER;

    /** OAuth realm (used to build JWKS/token endpoint URLs from auth server base) */
    String realm() default "kafka-authz";

    /** OAuth client ID for inter-broker auth */
    String clientId() default "kafka";

    /** OAuth client secret */
    String clientSecret() default "kafka-secret";

    /** OAuth username claim */
    String usernameClaim() default "preferred_username";

    /**
     * Additional OAuth JAAS properties (key=value format).
     * These are merged into the listener's sasl.jaas.config.
     * Example: {"oauth.check.audience=true", "oauth.custom.claim.check=..."}
     */
    String[] oauthProperties() default {};

    /**
     * Additional Kafka server properties (key=value format).
     * Applied via withAdditionalKafkaConfiguration().
     * Example: {"authorizer.class.name=io.strimzi...KeycloakAuthorizer"}
     */
    String[] kafkaProperties() default {};

    /** Whether to enable Prometheus JMX metrics on the Kafka container */
    boolean metrics() default false;

    /** Whether to auto-initialize MockOAuth endpoints to MODE_200 after Kafka starts */
    boolean initEndpoints() default true;

    /** Whether to auto-setup ACLs after Kafka starts */
    boolean setupAcls() default false;

    /**
     * SCRAM users to provision after Kafka starts (format: "username:password").
     * Each entry creates a SCRAM-SHA-512 credential via kafka-configs.sh.
     */
    String[] scramUsers() default {};

    /** Whether Kafka should be started (false means no Kafka container) */
    boolean enabled() default true;
}
