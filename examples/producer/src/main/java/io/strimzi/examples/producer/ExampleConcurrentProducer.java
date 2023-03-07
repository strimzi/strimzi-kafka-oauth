/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.examples.producer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * An example asynchronous (multi-threaded) producer implementation where multiple execution threads are managed by KafkaProducer
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
public class ExampleConcurrentProducer {

    private static Logger log = LoggerFactory.getLogger(ExampleConcurrentProducer.class);

    /**
     * A main method
     *
     * @param args No arguments expected
     */
    public static void main(String[] args) {

        String topic = "a_Topic1";

        Properties defaults = new Properties();
        Config external = new Config();

        configureTokenEndpoint(defaults, external);

        configureObtainingTheAccessToken(defaults, external);

        configureUsernameExtraction(defaults, external);

        // Resolve external configurations falling back to provided defaults
        ConfigProperties.resolveAndExportToSystemProperties(defaults);

        // Build the final config
        Properties props = buildProducerConfig();

        // Create the producer
        Producer<String, String> producer = new KafkaProducer<>(props);

        // KafkaProducer contains an internal worker pool and has an async API
        int messageCounter = 1;

        List<Job> jobs = new ArrayList<>();

        // Prepare initial batch of jobs
        for (int i = 0; i < 10; i++) {
            jobs.add(new Job("Message " + (i + messageCounter)));
        }
        messageCounter += 10;

        try {
            while (true) {

                // Run a batch of jobs
                runJobs(jobs, producer, topic);

                // Wait for all jobs to finish, and check if some jobs have encountered exceptions that require recreating the producer
                List<Job> rerunJobs = new ArrayList<>();
                boolean reinitProducer = false;

                // Wait for jobs to finish
                for (Job j : jobs) {
                    boolean reinit = waitForJob(j, rerunJobs);
                    if (reinit) {
                        reinitProducer = true;
                    }
                }

                // Refill the batch of jobs
                int toadd = jobs.size() - rerunJobs.size();
                for (int i = 0; i < toadd; i++) {
                    rerunJobs.add(new Job("Message " + (i + messageCounter)));
                }
                messageCounter += toadd;
                jobs = rerunJobs;

                // Make a little pause before the next batch
                sleep(10000);

                // Re-init producer if necessary
                if (reinitProducer) {
                    producer.close();
                    producer = new KafkaProducer<>(props);
                }
            }
        } finally {
            producer.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while sleeping!");
        }
    }

    private static boolean waitForJob(Job job, List<Job> rerunJobs) {
        boolean reinit = false;
        try {
            job.result.get();
            log.info("Sent '" + job.message + "'");
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while sending!");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AuthenticationException
                    || cause instanceof AuthorizationException) {
                reinit = true;
                rerunJobs.add(new Job(job.message));
                log.error("Failed to send message due to auth / authz issue ('" + job.message + "')", cause);
            } else {
                throw new RuntimeException("Failed to send message due to unexpected error ('" + job.message + "')", e);
            }
        }
        return reinit;
    }

    private static void runJobs(List<Job> jobs, Producer<String, String> producer, String topic) {
        for (Job j : jobs) {
            j.result = producer.send(new ProducerRecord<>(topic, j.message));
        }
    }

    private static void configureUsernameExtraction(Properties defaults, Config external) {
        // Use 'preferred_username' rather than 'sub' for principal name
        if (isAccessTokenJwt(external)) {
            defaults.setProperty(Config.OAUTH_USERNAME_CLAIM, "preferred_username");
        }
    }

    private static void configureObtainingTheAccessToken(Properties defaults, Config external) {
        //  By defaut this client uses preconfigured clientId and secret to authenticate.
        //  You can set OAUTH_ACCESS_TOKEN or OAUTH_REFRESH_TOKEN to override default authentication.
        //
        //  If access token is configured, it is passed directly to Kafka broker
        //  If refresh token is configured, it is used in conjunction with clientId and secret
        //
        //  See examples README.md for more info.

        final String accessToken = external.getValue(ClientConfig.OAUTH_ACCESS_TOKEN, null);

        if (accessToken == null) {
            defaults.setProperty(Config.OAUTH_CLIENT_ID, "team-a-client");
            defaults.setProperty(Config.OAUTH_CLIENT_SECRET, "team-a-client-secret");
        }
    }

    private static void configureTokenEndpoint(Properties defaults, Config external) {
        //  Set KEYCLOAK_HOST to connect to Keycloak host other than 'keycloak'
        //  Use 'keycloak.host' system property or KEYCLOAK_HOST env variable

        final String keycloakHost = external.getValue("keycloak.host", "keycloak");
        final String realm = external.getValue("realm", "kafka-authz");
        final String tokenEndpointUri = "http://" + keycloakHost + ":8080/auth/realms/" + realm + "/protocol/openid-connect/token";

        //  You can also configure token endpoint uri directly via 'oauth.token.endpoint.uri' system property,
        //  or OAUTH_TOKEN_ENDPOINT_URI env variable

        defaults.setProperty(ClientConfig.OAUTH_TOKEN_ENDPOINT_URI, tokenEndpointUri);
    }

    @SuppressWarnings("deprecation")
    private static boolean isAccessTokenJwt(Config config) {
        String legacy = config.getValue(Config.OAUTH_TOKENS_NOT_JWT);
        if (legacy != null) {
            System.out.println("[WARN] Config option 'oauth.tokens.not.jwt' is deprecated. Use 'oauth.access.token.is.jwt' (with reverse meaning) instead.");
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(Config.OAUTH_ACCESS_TOKEN_IS_JWT, true);
    }

    /**
     * Build KafkaProducer properties. The specified values are defaults that can be overridden
     * through runtime system properties or env variables.
     *
     * @return Configuration properties
     */
    private static Properties buildProducerConfig() {

        Properties p = new Properties();

        p.setProperty("security.protocol", "SASL_PLAINTEXT");
        p.setProperty("sasl.mechanism", "OAUTHBEARER");
        p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required ;");
        p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        p.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        // Adjust re-login options to fetch a fresh token for re-authentication
        // See: strimzi-kafka-oauth/README.md
        p.setProperty("sasl.login.refresh.buffer.seconds", "30");
        p.setProperty("sasl.login.refresh.min.period.seconds", "30");
        p.setProperty("sasl.login.refresh.window.factor", "0.8");
        p.setProperty("sasl.login.refresh.window.jitter", "0.01");

        return ConfigProperties.resolve(p);
    }

    static class Job {
        String message;
        Future<RecordMetadata> result = null;

        Job(String message) {
            this.message = message;
        }
    }
}
