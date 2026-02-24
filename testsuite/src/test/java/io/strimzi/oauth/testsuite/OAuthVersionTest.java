/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.oauth.testsuite.environment.AuthServer;
import io.strimzi.oauth.testsuite.environment.KafkaConfig;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironment;
import io.strimzi.oauth.testsuite.environment.OAuthEnvironmentExtension;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.oauth.testsuite.clients.KafkaClientsConfig.buildProducerConfigOAuthBearer;
import static io.strimzi.oauth.testsuite.clients.MockOAuthAdmin.changeAuthServerMode;
import static io.strimzi.oauth.testsuite.utils.KafkaClientsUtils.expectSaslAuthFailure;
import static io.strimzi.oauth.testsuite.utils.TestUtil.getContainerLogsForString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that verifies the locally-built OAuth SNAPSHOT JARs
 * are properly deployed and loaded inside the Kafka container.
 *
 * <p>Triggers a broker-side introspection failure (by turning off the auth server),
 * which produces a stack trace in the Kafka container logs. Java includes JAR filenames
 * in stack traces (e.g. {@code ~[kafka-oauth-server-1.0.0-SNAPSHOT.jar:?]}),
 * confirming that the locally-built SNAPSHOT JARs are loaded.
 */
@OAuthEnvironment(
    authServer = AuthServer.MOCK_OAUTH,
    kafka = @KafkaConfig(
        oauthProperties = {
            "oauth.introspection.endpoint.uri=https://mockoauth:8090/introspect",
            "oauth.client.id=kafka",
            "oauth.client.secret=kafka-secret",
            "oauth.valid.issuer.uri=https://mockoauth:8090",
            "unsecuredLoginStringClaim_sub=admin"
        }
    )
)
public class OAuthVersionTest {

    OAuthEnvironmentExtension env;

    @Test
    void testOAuthSnapshotJarsLoadedInKafka() throws Exception {
        // Turn off the auth server so introspection fails with "Connection refused"
        changeAuthServerMode("server", "mode_off");

        Map<String, String> oauthConfig = new HashMap<>();
        oauthConfig.put(ClientConfig.OAUTH_ACCESS_TOKEN, "mock.access.token");

        Properties producerProps = buildProducerConfigOAuthBearer(env.getBootstrapServers(), oauthConfig);

        // Send a token — the broker tries to introspect it, fails, and logs the error
        expectSaslAuthFailure(producerProps, "OAuthVersionIT-test", msg -> { });

        // The broker logs the introspection failure with a full stack trace.
        // Java includes JAR filenames in stack traces, e.g.:
        //   at io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler.handleCallback(...)
        //      ~[kafka-oauth-server-1.0.0-SNAPSHOT.jar:?]
        GenericContainer<?> kafka = env.getKafka();
        List<String> errorLog = getContainerLogsForString(kafka, "Runtime failure during token validation");
        String errorLines = String.join("\n", errorLog);

        // Make sure to change the version here if strimzi-kafka-oauth version change
        assertTrue(errorLines.contains("kafka-oauth-server-1.0.0-SNAPSHOT.jar:?"),
            "Container error stack traces should reference SNAPSHOT OAuth JARs, "
                + "confirming that the locally-built JARs are loaded by Kafka. Log:\n" + errorLines);
    }
}