/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.mockoauth;

import io.strimzi.kafka.oauth.metrics.GlobalConfig;
import io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler;
import io.strimzi.kafka.oauth.server.ServerConfig;

import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JaasServerConfigTest {

    public void doTest() throws Exception {
        testAllConfigOptions();
    }

    private void testAllConfigOptions() throws IOException {

        // Fast local JWT check
        JaasServerOauthValidatorCallbackHandler handler = new JaasServerOauthValidatorCallbackHandler();

        Map<String, String> attrs = new HashMap<>();

        // Fast local JWT check
        attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id");
        attrs.put(ServerConfig.OAUTH_JWKS_ENDPOINT_URI, "https://sso/jwks");
        attrs.put(ServerConfig.OAUTH_FAIL_FAST, "false");
        attrs.put(ServerConfig.OAUTH_USERNAME_CLAIM, "username-claim");
        attrs.put(ServerConfig.OAUTH_GROUPS_CLAIM, "$.groups");
        attrs.put(ServerConfig.OAUTH_GROUPS_CLAIM_DELIMITER, ",");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "client-id");
        attrs.put(ServerConfig.OAUTH_CHECK_AUDIENCE, "true");
        attrs.put(ServerConfig.OAUTH_CUSTOM_CLAIM_CHECK, "@.aud anyof ['kafka', 'something']");
        attrs.put(ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, "10");
        attrs.put(ServerConfig.OAUTH_JWKS_REFRESH_MIN_PAUSE_SECONDS, "2");
        attrs.put(ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, "900");
        attrs.put(ServerConfig.OAUTH_JWKS_IGNORE_KEY_USE, "true");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://sso");
        attrs.put(ServerConfig.OAUTH_CONNECT_TIMEOUT_SECONDS, "10");
        attrs.put(ServerConfig.OAUTH_READ_TIMEOUT_SECONDS, "10");
        attrs.put(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, "false");
        attrs.put(ServerConfig.OAUTH_ENABLE_METRICS, "true");
        attrs.put(GlobalConfig.STRIMZI_OAUTH_METRIC_REPORTERS, "io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        attrs.put(ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");
        attrs.put(ServerConfig.OAUTH_INCLUDE_ACCEPT_HEADER, "false");

        AppConfigurationEntry jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);

        Map<String, String> serverProps = new HashMap<>();
        serverProps.put("security.protocol", "SASL_PLAINTEXT");
        serverProps.put("sasl.mechanism", "OAUTHBEARER");

        LogLineReader logReader = new LogLineReader(Common.LOG_PATH);
        logReader.readNext();

        handler.configure(serverProps, "OAUTHBEARER", Arrays.asList(jaasConfig));

        Common.checkLog(logReader, "JWTSignatureValidator", "",
            "validatorId", "config-id",
            "keysEndpointUri", "https://sso/jwks",
            "groupsClaimQuery", "\\$\\.groups",
            "groupsClaimDelimiter", ",",
            "validIssuerUri", "https://sso",
            "hostnameVerifier", "SSLUtil",
            "sslSocketFactory", "SSLSocketFactoryImpl",
            "certsRefreshSeconds", "10",
            "certsRefreshMinPauseSeconds", "2",
            "certsExpirySeconds", "900",
            "certsIgnoreKeyUse", "true",
            "checkAccessTokenType", "false",
            "audience", "client-id",
            "customClaimCheck", "@\\.aud anyof \\['kafka', 'something'\\]",
            "connectTimeoutSeconds", "10",
            "readTimeoutSeconds", "10",
            "enableMetrics", "true",
            "failFast", "false",
            "includeAcceptHeader", "false"
        );

        // principalExtractor: PrincipalExtractor {usernameClaim: io.strimzi.kafka.oauth.common.PrincipalExtractor$Extractor@1e5f4170, fallbackUsernameClaim: null, fallbackUsernamePrefix: null}


        // Introspect endpoint
        attrs = new HashMap<>();
        attrs.put(ServerConfig.OAUTH_CONFIG_ID, "config-id2");
        attrs.put(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI, "https://sso/introspect");
        attrs.put(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI, "https://sso/userinfo");
        attrs.put(ServerConfig.OAUTH_USERNAME_CLAIM, "username-claim");
        attrs.put(ServerConfig.OAUTH_FALLBACK_USERNAME_CLAIM, "fallback-username-claim");
        attrs.put(ServerConfig.OAUTH_FALLBACK_USERNAME_PREFIX, "fallback-username-prefix");
        attrs.put(ServerConfig.OAUTH_GROUPS_CLAIM, "$.groups");
        attrs.put(ServerConfig.OAUTH_GROUPS_CLAIM_DELIMITER, ",");
        attrs.put(ServerConfig.OAUTH_CHECK_AUDIENCE, "true");
        attrs.put(ServerConfig.OAUTH_CUSTOM_CLAIM_CHECK, "@.aud anyof ['kafka', 'something']");
        attrs.put(ServerConfig.OAUTH_CLIENT_ID, "client-id");
        attrs.put(ServerConfig.OAUTH_CLIENT_SECRET, "client-secret");
        attrs.put(ServerConfig.OAUTH_VALID_ISSUER_URI, "https://sso");
        attrs.put(ServerConfig.OAUTH_VALID_TOKEN_TYPE, "jwt");
        attrs.put(ServerConfig.OAUTH_HTTP_RETRIES, "3");
        attrs.put(ServerConfig.OAUTH_HTTP_RETRY_PAUSE_MILLIS, "500");
        attrs.put(ServerConfig.OAUTH_CONNECT_TIMEOUT_SECONDS, "10");
        attrs.put(ServerConfig.OAUTH_READ_TIMEOUT_SECONDS, "10");
        attrs.put(ServerConfig.OAUTH_ENABLE_METRICS, "true");
        attrs.put(GlobalConfig.STRIMZI_OAUTH_METRIC_REPORTERS, "io.strimzi.testsuite.oauth.common.metrics.TestMetricsReporter");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_LOCATION, "../docker/target/kafka/certs/ca-truststore.p12");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_PASSWORD, "changeit");
        attrs.put(ServerConfig.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, "");
        attrs.put(ServerConfig.OAUTH_SSL_TRUSTSTORE_TYPE, "pkcs12");
        attrs.put(ServerConfig.OAUTH_INCLUDE_ACCEPT_HEADER, "false");

        jaasConfig = new AppConfigurationEntry("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, attrs);
        handler = new JaasServerOauthValidatorCallbackHandler();
        handler.configure(serverProps, "OAUTHBEARER", Arrays.asList(jaasConfig));

        Common.checkLog(logReader, "OAuthIntrospectionValidator", "",
            "id", "config-id2",
            "introspectionEndpointUri", "https://sso/introspect",
            "groupsClaimQuery", "\\$\\.groups",
            "groupsClaimDelimiter", ",",
            "validIssuerUri", "https://sso",
            "userInfoUri", "https://sso/userinfo",
            "hostnameVerifier", "SSLUtil",
            "sslSocketFactory", "SSLSocketFactoryImpl",
            "validTokenType", "jwt",
            "clientId", "client-id",
            "clientSecret", "c\\*\\*",
            "audience", "client-id",
            "customClaimCheck", "@\\.aud anyof \\['kafka', 'something'\\]",
            "connectTimeoutSeconds", "10",
            "readTimeoutSeconds", "10",
            "enableMetrics", "true",
            "retries", "3",
            "retryPauseMillis", "500",
            "includeAcceptHeader", "false"
        );

        //principalExtractor: PrincipalExtractor {usernameClaim: io.strimzi.kafka.oauth.common.PrincipalExtractor$Extractor@3bde62ff, fallbackUsernameClaim: io.strimzi.kafka.oauth.common.PrincipalExtractor$Extractor@523424b5, fallbackUsernamePrefix: fallback-username-prefix}
    }
}
