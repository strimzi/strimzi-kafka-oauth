/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

import io.strimzi.oauth.testsuite.logging.TestLogCollector;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative annotation for configuring an OAuth test environment.
 *
 * <p>Replaces the boilerplate pattern of manually creating environment objects,
 * registering log collectors, and managing lifecycle in setUp/tearDown methods.
 *
 * <p>Example usage:
 * <pre>{@code
 * @OAuthEnvironment(authServer = AuthServer.KEYCLOAK,
 *     kafka = @KafkaConfig(
 *         realm = "kafka-authz",
 *         oauthProperties = {"oauth.check.audience=true"},
 *         metrics = true
 *     ))
 * public class AudienceJwtIT {
 *     OAuthEnvironmentExtension env;  // auto-injected
 *
 *     @Test
 *     void myTest() {
 *         String bootstrap = env.getBootstrapServers();
 *         // ...
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestLogCollector
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OAuthEnvironmentExtension.class)
public @interface OAuthEnvironment {

    /** The type of OAuth authorization server to start */
    AuthServer authServer();

    /** Kafka container configuration */
    KafkaConfig kafka() default @KafkaConfig(enabled = false);
}
