/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.OAuthAuthenticator;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.SSLUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipal;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import io.strimzi.kafka.oauth.server.ServerConfig;
import io.strimzi.kafka.oauth.server.TestTokenFactory;
import io.strimzi.kafka.oauth.server.authorizer.AuthzConfig;
import io.strimzi.kafka.oauth.server.authorizer.KeycloakAuthorizer;
import io.strimzi.kafka.oauth.server.authorizer.KeycloakRBACAuthorizer;
import io.strimzi.kafka.oauth.server.authorizer.TestAuthzUtil;
import io.strimzi.testsuite.oauth.common.TestUtil;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SaslAuthenticationContext;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.strimzi.testsuite.oauth.mockoauth.Common.addGrantsForToken;
import static io.strimzi.testsuite.oauth.mockoauth.Common.changeAuthServerMode;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthClient;
import static io.strimzi.testsuite.oauth.mockoauth.Common.createOAuthUser;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeycloakAuthorizerTest {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthorizerTest.class);

    static final int LOOP_PAUSE_MS = 1000;
    static final int TIMEOUT_SECONDS = 30;

    static final String LOG_PATH = "target/test.log";

    static final String CLIENT_CLI = "kafka-cli";

    static final String USER_ALICE = "alice";
    static final String USER_ALICE_PASS = "alice-password";

    final static String TRUSTSTORE_PATH = "../docker/target/kafka/certs/ca-truststore.p12";
    final static String TRUSTSTORE_PASS = "changeit";

    final static String TOKEN_ENDPOINT = "https://mockoauth:8090/token";
    final static String FAILING_TOKEN_ENDPOINT = "https://mockoauth:8090/failing_token";

    final static String GRANTS_ENDPOINT = "https://mockoauth:8090/grants";

    final static String JWKS_ENDPOINT = "https://mockoauth:8090/jwks";
    final static String VALID_ISSUER_URI = "https://mockoauth:8090";


    final static String CLIENT_SRV = "kafka";
    final static String CLIENT_SRV_SECRET = "kafka-secret";

    public static void staticInit() throws IOException {
        // create a client for resource server
        createOAuthClient(CLIENT_SRV, CLIENT_SRV_SECRET);

        // create a client for user's client agent
        createOAuthClient(CLIENT_CLI, "");

        // create a user alice
        createOAuthUser(USER_ALICE, USER_ALICE_PASS);
    }

    public void doTests() throws Exception {
        doConfigTests();
        doMalformedGrantsTests();
        doHttpRetriesTest();
        doConcurrentGrantsRefreshTest();
        doGrantsGCTests();
        doGrants403Test();
        doSingletonTest();
    }

    private void doGrants403Test() throws IOException {

        logStart("KeycloakAuthorizerTest :: Grants 403 (no policies for user) Test");

        // Add a 403 test
        changeAuthServerMode("grants", "MODE_403");

        LogLineReader logReader = new LogLineReader(LOG_PATH);
        logReader.readNext();

        List<String> lines;
        HashMap<String, String> props = configureAuthorizer();
        props.put(AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI, "https://mockoauth:8090/grants");

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(props);

            TokenInfo tokenInfo = login(FAILING_TOKEN_ENDPOINT, USER_ALICE, USER_ALICE_PASS, 1);
            KafkaPrincipal principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, USER_ALICE, new Common.MockBearerTokenWithPayload(tokenInfo));
            AuthorizableRequestContext ctx = newAuthorizableRequestContext(principal);

            List<Action> actions = new ArrayList<>();
            actions.add(new Action(
                    AclOperation.CREATE,
                    new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                    1, true, true));

            List<AuthorizationResult> result = authorizer.authorize(ctx, actions);
            Assert.assertNotNull("Authorizer has to return non-null", result);
            Assert.assertEquals("Authorizer has to return as many results as it received inputs", result.size(), actions.size());
            Assert.assertEquals("Authorizer should return DENIED", AuthorizationResult.DENIED, result.get(0));

            lines = logReader.readNext();

            Assert.assertTrue("Saving non-null grants", checkLogForRegex(lines, "Saving non-null grants"));
            Assert.assertTrue("grants for user: {}", checkLogForRegex(lines, "grants for user .*: \\{\\}"));

        } finally {
            changeAuthServerMode("grants", "MODE_200");
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    void doHttpRetriesTest() throws IOException {
        logStart("KeycloakAuthorizerTest :: Grants HTTP Retries Tests");

        changeAuthServerMode("token", "MODE_200");
        changeAuthServerMode("failing_token", "MODE_400");

        HashMap<String, String> props = configureAuthorizer();
        props.put(AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI, "https://mockoauth:8090/failing_grants");

        try (KeycloakRBACAuthorizer authorizer = new KeycloakRBACAuthorizer()) {
            authorizer.configure(props);

            try {
                login(FAILING_TOKEN_ENDPOINT, USER_ALICE, USER_ALICE_PASS, 0);

                Assert.fail("Should have failed while logging in with password");

            } catch (Exception expected) {
                login(FAILING_TOKEN_ENDPOINT, USER_ALICE, USER_ALICE_PASS, 0);
            }

            // Now try again
            TokenInfo tokenInfo = login(FAILING_TOKEN_ENDPOINT, USER_ALICE, USER_ALICE_PASS, 1);
            KafkaPrincipal principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, USER_ALICE, new Common.MockBearerTokenWithPayload(tokenInfo));
            AuthorizableRequestContext ctx = newAuthorizableRequestContext(principal);

            List<Action> actions = new ArrayList<>();
            actions.add(new Action(
                    AclOperation.CREATE,
                    new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                    1, true, true));

            List<AuthorizationResult> result = authorizer.authorize(ctx, actions);
            Assert.assertNotNull("Authorizer has to return non-null", result);
            Assert.assertEquals("Authorizer has to return as many results as it received inputs", result.size(), actions.size());
            Assert.assertEquals("Authorizer should return ALLOWED", AuthorizationResult.ALLOWED, result.get(0));
        }
    }

    /**
     * This test makes sure the concurrent threads needing grants for the same user that is not yet available in grants cache
     * result in a single request to the Keycloak server, with second thread waiting for result and reusing it.
     *
     * @throws IOException If an exception occurs during I/O operation
     * @throws ExecutionException If an exception occurs during job execution
     * @throws InterruptedException If test is interrupted
     */
    void doConcurrentGrantsRefreshTest() throws IOException, ExecutionException, InterruptedException {
        logStart("KeycloakAuthorizerTest :: Concurrent Grants Refresh Tests");

        // create a test user 'user1'
        String userOne = "user1";
        String userOnePass = "user1-password";
        createOAuthUser(userOne, userOnePass);

        changeAuthServerMode("token", "MODE_200");
        changeAuthServerMode("failing_token", "MODE_400");

        // grants endpoint has to be configured to respond with a 1s delay
        changeAuthServerMode("grants", "MODE_200_DELAYED");

        // one test uses KeycloakAuthorizer not configured with 'strimzi.authorization.reuse.grants'
        HashMap<String, String> props = configureAuthorizer();
        runConcurrentFetchGrantsTest(props, true, userOne, userOnePass);

        // another test uses KeycloakAuthorizer configured with it set to 'false'
        props.put("strimzi.authorization.reuse.grants", "false");
        runConcurrentFetchGrantsTest(props, false, userOne, userOnePass);
    }

    private void runConcurrentFetchGrantsTest(HashMap<String, String> props, boolean withReuse, String user, String userPass) throws IOException, ExecutionException, InterruptedException {

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(props);

            LogLineReader logReader = new LogLineReader(LOG_PATH);
            List<String> lines = logReader.readNext();

            if (withReuse) {
                Assert.assertTrue("reuseGrants should be true", checkLogForRegex(lines, "reuseGrants: true"));
            } else {
                Assert.assertTrue("reuseGrants should default to false", checkLogForRegex(lines, "reuseGrants: false"));
            }

            TokenInfo tokenInfo = login(FAILING_TOKEN_ENDPOINT, user, userPass, 1);
            OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, user, TestTokenFactory.newTokenForUser(tokenInfo));

            addGrantsForToken(tokenInfo.token(), "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Create\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topic:my-topic*\"}," +
                    "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                    "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]");

            AuthorizableRequestContext ctx = newAuthorizableRequestContext(principal);


            ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                Assert.assertNull("payload should not be set yet: " + principal.getJwt().getPayload(), principal.getJwt().getPayload());

                // In two parallel threads invoke authorize() passing packaged principal
                Future<List<AuthorizationResult>> future = submitAuthorizationCall(authorizer, ctx, executorService, "my-topic");
                Future<List<AuthorizationResult>> future2 = submitAuthorizationCall(authorizer, ctx, executorService, "my-topic-2");

                List<AuthorizationResult> result = future.get();
                List<AuthorizationResult> result2 = future2.get();

                // Check log output for signs of semaphore doing its job
                // and only fetching the grants once, then reusing the fetched grants by the other thread
                // It's the same whether reuseGrants is true or false - because concurrent requests are perceived by user to occur at the same time
                lines = logReader.readNext();

                Assert.assertEquals("One thread fetches grants", 1, countLogForRegex(lines, "Fetching grants from Keycloak for user user1"));
                Assert.assertEquals("One thread waits", 1, countLogForRegex(lines, "Waiting on another thread to get grants"));
                Assert.assertEquals("One grants fetch", 1, countLogForRegex(lines, "Response body for POST https://mockoauth:8090/grants"));

                // Check the authorization result
                Assert.assertEquals("One result for my-topic action", 1, result.size());
                Assert.assertEquals("One result for my-topic-2 action", 1, result2.size());
                Assert.assertEquals("my-topic ALLOWED", AuthorizationResult.ALLOWED, result.get(0));
                Assert.assertEquals("my-topic-2 ALLOWED", AuthorizationResult.ALLOWED, result.get(0));

                if (!withReuse) {
                    // Check that the BearerTokenWithJsonPayload has a payload
                    // That only gets set in no-reuse regime in order to be able to determine if grants were refreshed for the session
                    Assert.assertNotNull("payload should be set now: " + principal.getJwt().getPayload(), principal.getJwt().getPayload());
                }


                // Perform another authorization - grants should be retrieved directly from grants cache,
                // even if reuseGrants is false, because it's not a new session anymore
                future = submitAuthorizationCall(authorizer, ctx, executorService, "x-topic-1");
                result = future.get();

                // check log from last checkpoint on
                lines = logReader.readNext();

                Assert.assertEquals("No grants fetch", 0, countLogForRegex(lines, "Response body for POST https://mockoauth:8090/grants"));

                // Check the authorization result
                Assert.assertEquals("One result for x-topic-1 action", 1, result.size());
                Assert.assertEquals("x-topic-1 DENIED", AuthorizationResult.DENIED, result.get(0));

                // Create a new Principal object for the same user
                // Perform another authorization - grants should be fetched if reuseGrants is false
                principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, user, TestTokenFactory.newTokenForUser(tokenInfo));
                ctx = newAuthorizableRequestContext(principal);

                future = submitAuthorizationCall(authorizer, ctx, executorService, "x-topic-2");
                result = future.get();

                lines = logReader.readNext();
                if (!withReuse) {
                    // Check that grants have been fetched
                    Assert.assertEquals("Grants fetched", 1, countLogForRegex(lines, "Response body for POST https://mockoauth:8090/grants"));
                } else {
                    // Check that grants have not been fetched again
                    Assert.assertEquals("Grants not fetched", 0, countLogForRegex(lines, "Response body for POST https://mockoauth:8090/grants"));
                }

                // Check the authorization result
                Assert.assertEquals("One result for x-topic-2 action", 1, result.size());
                Assert.assertEquals("x-topic-2 DENIED", AuthorizationResult.DENIED, result.get(0));
            } finally {
                executorService.shutdown();
            }
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    void doConfigTests() throws IOException {
        logStart("KeycloakAuthorizerTest :: Config Tests");

        HashMap<String, String> config = new HashMap<>();

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            try {
                authorizer.configure(config);
                Assert.fail("Should have failed");
            } catch (ConfigException e) {
                Assert.assertTrue("'principal.builder.class' is missing", e.getMessage().contains("KeycloakRBACAuthorizer requires io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder as 'principal.builder.class'"));
            }
        }
        config.put("principal.builder.class", "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            try {
                authorizer.configure(config);
                Assert.fail("Should have failed");
            } catch (ConfigException e) {
                Assert.assertTrue("'strimzi.authorization.token.endpoint.uri' is missing", e.getMessage().contains("Token Endpoint ('strimzi.authorization.token.endpoint.uri') not set"));
            }
        }
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI, GRANTS_ENDPOINT);

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            try {
                authorizer.configure(config);
                Assert.fail("Should have failed");
            } catch (ConfigException e) {
                Assert.assertTrue("'strimzi.authorization.client.id' is missing", e.getMessage().contains("client id ('strimzi.authorization.client.id') not set"));
            }
        }
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_CLIENT_ID, "kafka");

        LogLineReader logReader = new LogLineReader(LOG_PATH);

        // Position to the end of the existing log file
        logReader.readNext();

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(config);
        }

        List<String> lines = logReader.readNext();

        // Check the defaults
        Assert.assertEquals("tokenEndpointUri: https://mockoauth:8090/grants", 1, countLogForRegex(lines, "tokenEndpointUri: https://mockoauth:8090/grants"));
        Assert.assertEquals("clientId: kafka", 1, countLogForRegex(lines, "clientId: kafka"));
        Assert.assertEquals("sslSocketFactory: null", 1, countLogForRegex(lines, "sslSocketFactory: null"));
        Assert.assertEquals("hostnameVerifier: null", 1, countLogForRegex(lines, "hostnameVerifier: null"));
        Assert.assertEquals("clusterName: kafka-cluster", 1, countLogForRegex(lines, "clusterName: kafka-cluster"));
        Assert.assertEquals("delegateToKafkaACL: false", 1, countLogForRegex(lines, "delegateToKafkaACL: false"));
        Assert.assertEquals("superUsers: []", 1, countLogForRegex(lines, "superUsers: \\[\\]"));
        Assert.assertEquals("grantsRefreshPeriodSeconds: 60", 1, countLogForRegex(lines, "grantsRefreshPeriodSeconds: 60"));
        Assert.assertEquals("grantsRefreshPoolSize: 5", 1, countLogForRegex(lines, "grantsRefreshPoolSize: 5"));
        Assert.assertEquals("grantsMaxIdleTimeSeconds: 300", 1, countLogForRegex(lines, "grantsMaxIdleTimeSeconds: 300"));
        Assert.assertEquals("httpRetries: 0", 1, countLogForRegex(lines, "httpRetries: 0"));
        Assert.assertEquals("reuseGrants: true", 1, countLogForRegex(lines, "reuseGrants: true"));
        Assert.assertEquals("connectTimeoutSeconds: 60", 1, countLogForRegex(lines, "connectTimeoutSeconds: 60"));
        Assert.assertEquals("readTimeoutSeconds: 60", 1, countLogForRegex(lines, "readTimeoutSeconds: 60"));
        Assert.assertEquals("enableMetrics: false", 1, countLogForRegex(lines, "enableMetrics: false"));
        Assert.assertEquals("gcPeriodSeconds: 300", 1, countLogForRegex(lines, "gcPeriodSeconds: 300"));

        // Custom config

        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME, "cluster1");
        config.put("super.users", "User:admin;User:service-account-kafka");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, "180");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE, "3");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS, "30");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_HTTP_RETRIES, "2");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_REUSE_GRANTS, "false");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS, "15");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS, "15");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_ENABLE_METRICS, "true");
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS, "60");

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            try {
                authorizer.configure(config);
                Assert.fail("Should have failed");
            } catch (ConfigException e) {
                Assert.assertEquals("Only one instance per JVM", "Only one authorizer configuration per JVM is supported", e.getMessage());
            }
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(config);
        }

        lines = logReader.readNext();

        Assert.assertEquals("clusterName: cluster1", 1, countLogForRegex(lines, "clusterName: cluster1"));
        Assert.assertEquals("superUsers: ['User:admin', 'User:service-account-kafka']", 1, countLogForRegex(lines, "superUsers: \\['User:admin', 'User:service-account-kafka'\\]"));
        Assert.assertEquals("grantsRefreshPeriodSeconds: 180", 1, countLogForRegex(lines, "grantsRefreshPeriodSeconds: 180"));
        Assert.assertEquals("grantsRefreshPoolSize: 3", 1, countLogForRegex(lines, "grantsRefreshPoolSize: 3"));
        Assert.assertEquals("grantsMaxIdleTimeSeconds: 30", 1, countLogForRegex(lines, "grantsMaxIdleTimeSeconds: 30"));
        Assert.assertEquals("httpRetries: 2", 1, countLogForRegex(lines, "httpRetries: 2"));
        Assert.assertEquals("reuseGrants: false", 1, countLogForRegex(lines, "reuseGrants: false"));
        Assert.assertEquals("connectTimeoutSeconds: 15", 1, countLogForRegex(lines, "connectTimeoutSeconds: 15"));
        Assert.assertEquals("readTimeoutSeconds: 15", 1, countLogForRegex(lines, "readTimeoutSeconds: 15"));
        Assert.assertEquals("enableMetrics: true", 1, countLogForRegex(lines, "enableMetrics: true"));
        Assert.assertEquals("gcPeriodSeconds: 60", 1, countLogForRegex(lines, "gcPeriodSeconds: 60"));


        // test gcPeriodSeconds set to 0
        config.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS, "0");

        TestAuthzUtil.clearKeycloakAuthorizerService();
        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(config);
        }

        lines = logReader.readNext();

        Assert.assertEquals("gcPeriodSeconds invalid value: 0", 1, countLogForRegex(lines, "'strimzi.authorization.grants.gc.period.seconds' set to invalid value: 0, using the default value: 300 seconds"));
        Assert.assertEquals("gcPeriodSeconds: 300", 1, countLogForRegex(lines, "gcPeriodSeconds: 300"));

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    void doGrantsGCTests() throws Exception {
        logStart("KeycloakAuthorizerTest :: Grants Garbage Collection Tests");

        // make sure the token endpoint works fine
        changeAuthServerMode("token", "MODE_200");

        // Make sure grants endpoint is set to normal mode 200
        changeAuthServerMode("grants", "MODE_200");

        String userOne = "gcUser1";
        String userOnePass = "gcUser1-password";
        createOAuthUser(userOne, userOnePass);

        // Set gcPeriodSeconds to 3 seconds
        HashMap<String, String> props = configureAuthorizer();
        props.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, "5");
        props.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS, "3");

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(props);

            // Perform authentications and authorizations with different access tokens for the same user
            // That will populate the grantsCache map with a single entry and update already existing entry with latest access token

            // authentication
            TokenInfo tokenInfo = login(TOKEN_ENDPOINT, userOne, userOnePass, 0);

            //   simulate an authenticated session
            changeAuthServerMode("jwks", "MODE_200");

            //     configure the authentication handler
            AuthenticateCallbackHandler authHandler = configureJwtSignatureValidator();


            // check the logs for updated access token
            LogLineReader logReader = new LogLineReader(LOG_PATH);

            // wait for cgGrants run on 0 users
            LOG.info("Waiting for: active users count: 0"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Grants gc: active users count: 0");

            LOG.info("Authenticate (validate) as gcUser1");
            OAuthKafkaPrincipal principal = authenticate(authHandler, tokenInfo);


            // authorization

            AuthorizableRequestContext authzContext = newAuthorizableRequestContext(principal);

            List<Action> actions = new ArrayList<>();
            actions.add(new Action(
                    AclOperation.CREATE,
                    new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                    1, true, true));

            //   perform authorization for the session
            LOG.info("Call authorize() as gcUser1");
            List<AuthorizationResult> result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: ALLOWED", AuthorizationResult.ALLOWED, result.get(0));

            // check the logs for updated access token
            List<String> lines = logReader.readNext();
            Assert.assertEquals("Fetch grants", 1, countLogForRegex(lines, "Fetching grants from Keycloak for user gcUser1"));


            String userTwo = "gcUser2";
            String userTwoPass = "gcUser2-password";
            createOAuthUser(userTwo, userTwoPass, 8);

            // Create a short-lived access token for a new user, that only has a 5 seconds lifetime
            // This allows us to test if after 5 seconds the triggered gc job cleans the cache, due to the expired token
            tokenInfo = login(TOKEN_ENDPOINT, userTwo, userTwoPass, 0);

            LOG.info("Authenticate (validate) gcUser2");
            principal = authenticate(authHandler, tokenInfo);

            LOG.info("Waiting for: active users count: 2, grantsCache size before: 1, grantsCache size after: 1"); // Make sure to not repeat the below condition in the string here
            // wait for cgGrants run on 2 users
            waitFor(logReader, "Grants gc: active users count: 2, grantsCache size before: 1, grantsCache size after: 1");


            authzContext = newAuthorizableRequestContext(principal);

            LOG.info("Call authorize() as gcUser2");
            result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: ALLOWED", AuthorizationResult.ALLOWED, result.get(0));

            // wait for cgGrants run on 2 users and two grants cache entries
            LOG.info("Waiting for: active users count: 2, grantsCache size before: 2, grantsCache size after: 2"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Grants gc: active users count: 2, grantsCache size before: 2, grantsCache size after: 2");


            // now wait for token to expire for gcUser2
            LOG.info("Waiting for: active users count: 1, grantsCache size before: 2, grantsCache size after: 1"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Grants gc: active users count: 1, grantsCache size before: 2, grantsCache size after: 1");


            // authorization should now fail since the token has expired
            LOG.info("Authorize another action for gcUser2");
            result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: DENIED", AuthorizationResult.DENIED, result.get(0));
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    private OAuthKafkaPrincipal authenticate(AuthenticateCallbackHandler authHandler, TokenInfo tokenInfo) throws IOException {

        // authenticate with the raw access token, get BearerTokenWithPayload that represents the session
        BearerTokenWithPayload tokenWithPayload = null;
        try {
            tokenWithPayload = (BearerTokenWithPayload) authenticate(authHandler, tokenInfo.token());
        } catch (UnsupportedCallbackException e) {
            Assert.fail("Test error - should never happen: " + e);
        }

        // mock up the authentication workflow part that creates the OAuthKafkaPrincipal
        OAuthKafkaPrincipalBuilder principalBuilder = new OAuthKafkaPrincipalBuilder();
        principalBuilder.configure(new HashMap<>());

        OAuthBearerSaslServer saslServer = mock(OAuthBearerSaslServer.class);
        when(saslServer.getMechanismName()).thenReturn("OAUTHBEARER");
        when(saslServer.getAuthorizationID()).thenReturn(tokenInfo.principal());
        when(saslServer.getNegotiatedProperty("OAUTHBEARER.token")).thenReturn(tokenWithPayload);

        SaslAuthenticationContext authContext = mock(SaslAuthenticationContext.class);
        when(authContext.server()).thenReturn(saslServer);

        return (OAuthKafkaPrincipal) principalBuilder.build(authContext);
    }

    private AuthenticateCallbackHandler configureJwtSignatureValidator() {
        //JWTSignatureValidator validator = new JWTSignatureValidator("test-validator", JWKS_ENDPOINT, )
        JaasServerOauthValidatorCallbackHandler authHandler = new JaasServerOauthValidatorCallbackHandler();
        Map<String, String> jaasProps = new HashMap<>();
        jaasProps.put(ServerConfig.OAUTH_JWKS_ENDPOINT_URI, JWKS_ENDPOINT);
        jaasProps.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, TRUSTSTORE_PATH);
        jaasProps.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, TRUSTSTORE_PASS);
        jaasProps.put(ServerConfig.OAUTH_VALID_ISSUER_URI, VALID_ISSUER_URI);
        jaasProps.put(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, "false");

        Map<String, String> configs = new HashMap<>();
        authHandler.configure(configs, "OAUTHBEARER", Collections.singletonList(new AppConfigurationEntry("server", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, jaasProps)));
        return authHandler;
    }

    private OAuthBearerToken authenticate(AuthenticateCallbackHandler callbackHandler, String accessToken) throws IOException, UnsupportedCallbackException {
        OAuthBearerValidatorCallback callback = new OAuthBearerValidatorCallback(accessToken);
        Callback[] callbacks = new Callback[] {callback};
        callbackHandler.handle(callbacks);
        return callback.token();
    }

    /**
     * Test for the handling of improperly configured Authorization Services
     */
    void doMalformedGrantsTests() throws IOException, InterruptedException, TimeoutException {
        logStart("KeycloakAuthorizerTest :: Malformed Grants Tests");

        // make sure the token endpoint works fine
        changeAuthServerMode("token", "MODE_200");

        // login as some user - alice in our case, and get the token
        TokenInfo tokenInfo = login(TOKEN_ENDPOINT, USER_ALICE, USER_ALICE_PASS, 0);
        OAuthKafkaPrincipal principal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE, USER_ALICE, TestTokenFactory.newTokenForUser(tokenInfo));

        // Mistyped resource type 'Topc' instead of 'Topic'
        addGrantsForToken(tokenInfo.token(), "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Create\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topc:my-topic*\"}," +
                "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]");

        List<Action> actions = new ArrayList<>();
        actions.add(new Action(
                AclOperation.CREATE,
                new ResourcePattern(ResourceType.TOPIC, "my-topic", PatternType.LITERAL),
                1, true, true));

        LogLineReader logReader = new LogLineReader(LOG_PATH);
        // seek to the end of log file
        logReader.readNext();

        HashMap<String, String> props = configureAuthorizer();
        props.put(AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, "2");

        try (KeycloakAuthorizer authorizer = new KeycloakAuthorizer()) {
            authorizer.configure(props);


            AuthorizableRequestContext authzContext = newAuthorizableRequestContext(principal);

            LOG.info("Call authorize() - test grants record with invalid resource type 'Topc'");
            List<AuthorizationResult> result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: DENIED", AuthorizationResult.DENIED, result.get(0));

            // This is a first authorize() call on the KeycloakAuthorizer -> the grantsCache is empty
            LOG.info("Waiting for: unsupported segment type: Topc"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Failed to parse .* unsupported segment type: Topc");


            // malformed resource spec - no ':' in Topic;my-topic*
            addGrantsForToken(tokenInfo.token(), "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Create\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topic;my-topic*\"}," +
                    "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                    "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]");

            // wait for grants refresh
            LOG.info("Waiting for: Done refreshing grants"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Response body .*Topic;my-topic");


            LOG.info("Call authorize() - test grants record with malformed resource spec 'Topic;my-topic*' (no ':')");
            result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: DENIED", AuthorizationResult.DENIED, result.get(0));

            LOG.info("Waiting for: doesn't follow TYPE:NAME pattern"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "part doesn't follow TYPE:NAME pattern");

            // malformed resource spec - '*' not at the end in 'Topic:*-topic'
            addGrantsForToken(tokenInfo.token(), "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Create\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topic:*-topic\"}," +
                    "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                    "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]");

            // wait for grants refresh
            LOG.info("Waiting for: Done refreshing grants"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Response body .*Topic:\\*-topic");

            LOG.info("Call authorize() - test grants record with malformed resource spec 'Topic:*-topic' ('*' only interpreted as asterisk at the end of resource spec)");
            result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: DENIED", AuthorizationResult.DENIED, result.get(0));


            // unknown scope - 'Crate' (should be 'Create')
            addGrantsForToken(tokenInfo.token(), "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Crate\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topic:my-topic*\"}," +
                    "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                    "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]");

            // wait for grants refresh
            LOG.info("Waiting for: Done refreshing grants"); // Make sure to not repeat the below condition in the string here
            waitFor(logReader, "Response body .*Crate");

            LOG.info("Call authorize() - test grants record with unknown / invalid scope 'Crate' (it should be 'Create')");
            result = authorizer.authorize(authzContext, actions);
            Assert.assertEquals("Authz result: DENIED", AuthorizationResult.DENIED, result.get(0));
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    public void doSingletonTest() throws Exception {
        logStart("KeycloakAuthorizerTest :: Ensure that multiple instantiated KeycloakAuthorizers share a single instance of KeycloakRBACAuthorizer");

        HashMap<String, String> config = configureAuthorizer();

        LogLineReader logReader = new LogLineReader(LOG_PATH);
        logReader.readNext();

        List<String> lines;
        try (KeycloakAuthorizer authorizer1 = new KeycloakAuthorizer();
             KeycloakAuthorizer authorizer2 = new KeycloakAuthorizer()) {

            authorizer1.configure(config);
            authorizer2.configure(config);

            lines = logReader.readNext();

            List<String> keycloakAuthorizerLines = lines.stream().filter(line -> line.contains("Configured KeycloakAuthorizer@")).collect(Collectors.toList());
            List<String> keycloakRBACAuthorizerLines = lines.stream().filter(line -> line.contains("Configured KeycloakRBACAuthorizer@")).collect(Collectors.toList());

            Assert.assertEquals("Configured KeycloakAuthorizer", 2, keycloakAuthorizerLines.size());
            Assert.assertEquals("Configured KeycloakRBACAuthorizer", 1, keycloakRBACAuthorizerLines.size());
        }

        TestAuthzUtil.clearKeycloakAuthorizerService();
    }

    private void waitFor(LogLineReader logReader, String condition) throws TimeoutException, InterruptedException {
        TestUtil.waitForCondition(() -> {
            try {
                List<String> lines = logReader.readNext();
                return countLogForRegex(lines, condition) > 0;
            } catch (Exception e) {
                throw new RuntimeException("Failed to read log");
            }
        }, LOOP_PAUSE_MS, TIMEOUT_SECONDS);
    }

    private static Future<List<AuthorizationResult>> submitAuthorizationCall(KeycloakAuthorizer authorizer, AuthorizableRequestContext ctx, ExecutorService executorService, String topic) {
        return executorService.submit(() -> {
            List<Action> actions = new ArrayList<>();
            actions.add(new Action(
                    AclOperation.CREATE,
                    new ResourcePattern(ResourceType.TOPIC, topic, PatternType.LITERAL),
                    1, true, true));

            return authorizer.authorize(ctx, actions);
        });
    }

    private HashMap<String, String> configureAuthorizer() {
        return configureAuthorizer(CLIENT_SRV, CLIENT_SRV_SECRET, TRUSTSTORE_PATH, TRUSTSTORE_PASS);
    }

    static HashMap<String, String> configureAuthorizer(String clientSrv, String clientSrvSecret, String trustStorePath, String trustsStorePass) {
        HashMap<String, String> props = new HashMap<>();
        props.put("strimzi.authorization.ssl.truststore.location", trustStorePath);
        props.put("strimzi.authorization.ssl.truststore.password", trustsStorePass);
        props.put("strimzi.authorization.ssl.truststore.type", "pkcs12");

        props.put("strimzi.authorization.enable.metrics", "true");
        props.put("strimzi.authorization.token.endpoint.uri", "https://mockoauth:8090/grants");
        props.put("strimzi.authorization.client.id", clientSrv);
        props.put("strimzi.authorization.client.secret", clientSrvSecret);
        props.put("strimzi.authorization.kafka.cluster.name", "my-cluster");
        props.put("strimzi.authorization.delegate.to.kafka.acl", "false");
        props.put("strimzi.authorization.read.timeout.seconds", "45");
        props.put("strimzi.authorization.connect.timeout.seconds", "10");
        props.put("strimzi.authorization.grants.refresh.pool.size", "2");
        props.put("strimzi.authorization.grants.refresh.period.seconds", "60");
        props.put("strimzi.authorization.http.retries", "1");
        props.put("super.users", "User:admin;User:service-account-kafka");
        props.put("principal.builder.class", "io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder");
        return props;
    }

    static int countLogForRegex(List<String> log, String regex) {
        int count = 0;
        Pattern pattern = Pattern.compile(prepareRegex(regex));
        for (String line: log) {
            if (pattern.matcher(line).matches()) {
                count += 1;
            }
        }
        return count;
    }

    static String prepareRegex(String regex) {
        String prefix = regex.startsWith("^") ? "" : ".*";
        String suffix = regex.endsWith("$") ? "" : ".*";
        return prefix + regex + suffix;
    }

    static boolean checkLogForRegex(List<String> log, String regex) {
        Pattern pattern = Pattern.compile(prepareRegex(regex));
        for (String line: log) {
            if (pattern.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    private AuthorizableRequestContext newAuthorizableRequestContext(KafkaPrincipal principal) {
        AuthorizableRequestContext ctx = mock(AuthorizableRequestContext.class);
        when(ctx.listenerName()).thenReturn("JWT");
        when(ctx.securityProtocol()).thenReturn(SecurityProtocol.SASL_PLAINTEXT);
        when(ctx.principal()).thenReturn(principal);
        when(ctx.clientId()).thenReturn(CLIENT_CLI);
        return ctx;
    }

    private TokenInfo login(String tokenEndpoint, String user, String userPass, int retries) throws IOException {
        return OAuthAuthenticator.loginWithPassword(
                URI.create(tokenEndpoint),
                SSLUtil.createSSLFactory(TRUSTSTORE_PATH, null, TRUSTSTORE_PASS, null, null),
                null,
                user,
                userPass,
                CLIENT_CLI,
                null,
                true,
                new PrincipalExtractor(),
                "all",
                null,
                60,
                60,
                retries,
                0);
    }

    private void logStart(String msg) {
        System.out.println();
        System.out.println("========    "  + msg);
        System.out.println();

        // Log to file as well for better readability
        LOG.info("========    " + msg);
    }
}
