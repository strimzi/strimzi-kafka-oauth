/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.client.ClientConfig;
import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigException;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.client.ClientConfig.OAUTH_TOKEN_ENDPOINT_URI;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_CONNECT_TIMEOUT_SECONDS;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_ENABLE_METRICS;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_READ_TIMEOUT_SECONDS;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_TRUSTSTORE_CERTIFICATES;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_TRUSTSTORE_LOCATION;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_TRUSTSTORE_PASSWORD;
import static io.strimzi.kafka.oauth.common.Config.OAUTH_SSL_TRUSTSTORE_TYPE;
import static io.strimzi.kafka.oauth.common.Config.isTrue;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_CLIENT_ID;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_ENABLE_METRICS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_HTTP_RETRIES;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_REUSE_GRANTS;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI;
import static io.strimzi.kafka.oauth.server.authorizer.AuthzConfig.STRIMZI_OAUTH_INCLUDE_ACCEPT_HEADER;

/**
 * The classes used to parse and store Authorizer configuration.
 * It is used to compare two configurations for equality and used by KeycloakAuthorizer to maintain a single instance of active Authorizer
 * in the JVM even when multiple authorizers are instantiated from a single authorizer configuration.
 */
@SuppressWarnings({"deprecation"})
public class Configuration {

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);
    private static final String PRINCIPAL_BUILDER_CLASS = OAuthKafkaPrincipalBuilder.class.getName();
    private static final String DEPRECATED_PRINCIPAL_BUILDER_CLASS = JwtKafkaPrincipalBuilder.class.getName();

    private final Map<String, ?> configMap;

    private final List<Log> logs = new LinkedList<>();


    private final boolean reuseGrants;
    private final String clientId;
    private final String clusterName;
    private final boolean delegateToKafkaACL;
    private final int grantsRefreshPeriodSeconds;
    private final int grantsMaxIdleTimeSeconds;
    private final int grantsRefreshPoolSize;
    private final int gcPeriodSeconds;
    private boolean isKRaft;
    private String truststore;
    private String truststoreData;
    private String truststorePassword;
    private String truststoreType;
    private String prng;
    private String certificateHostCheckAlgorithm;
    private List<UserSpec> superUsers = Collections.emptyList();
    private int httpRetries;
    private boolean enableMetrics;
    private URI tokenEndpointUrl;
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;
    private boolean includeAcceptHeader;

    /**
     * Create a new Configuration instance
     * <p>
     * If some configuration is invalid in a way that it can't be automatically fixed, the {@link ConfigException} is thrown.
     *
     * @param configs Configuration map passed by Kafka broker to {@link org.apache.kafka.server.authorizer.Authorizer#configure} method
     */
    Configuration(Map<String, ?> configs) {

        this.configMap = configs;

        AuthzConfig authzConfig = convertToAuthzConfig(configs);

        String pbclass = (String) configMap.get("principal.builder.class");
        if (!PRINCIPAL_BUILDER_CLASS.equals(pbclass) && !DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            throw new ConfigException("This authorizer requires " + PRINCIPAL_BUILDER_CLASS + " as 'principal.builder.class'");
        }

        if (DEPRECATED_PRINCIPAL_BUILDER_CLASS.equals(pbclass)) {
            logs.add(new Log(Log.Level.WARNING, "The '" + DEPRECATED_PRINCIPAL_BUILDER_CLASS + "' class has been deprecated, and may be removed in the future. Please use '" + PRINCIPAL_BUILDER_CLASS + "' as 'principal.builder.class' instead."));
        }

        configureTokenEndpoint(authzConfig);

        clientId = ConfigUtil.getConfigWithFallbackLookup(authzConfig, STRIMZI_AUTHORIZATION_CLIENT_ID, ClientConfig.OAUTH_CLIENT_ID);
        if (clientId == null) {
            throw new ConfigException("OAuth client id ('" + STRIMZI_AUTHORIZATION_CLIENT_ID + "') not set.");
        }

        configureSSLFactory(authzConfig);
        configureHostnameVerifier(authzConfig);
        configureHttpTimeouts(authzConfig);

        String clusterName = authzConfig.getValue(STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME);
        if (clusterName == null) {
            clusterName = "kafka-cluster";
        }
        this.clusterName = clusterName;

        delegateToKafkaACL = authzConfig.getValueAsBoolean(STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL, false);


        configureSuperUsers(configs);

        // Number of threads that can perform token endpoint requests at the same time
        grantsRefreshPoolSize = authzConfig.getValueAsInt(STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE, 5);
        if (grantsRefreshPoolSize < 1) {
            throw new ConfigException("Invalid value of '" + STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE + "': " + grantsRefreshPoolSize + ". Has to be >= 1.");
        }

        // Less or equal zero means to never refresh
        grantsRefreshPeriodSeconds = authzConfig.getValueAsInt(STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS, 60);
        grantsMaxIdleTimeSeconds = configureGrantsMaxIdleTimeSeconds(authzConfig);

        gcPeriodSeconds = configureGcPeriodSeconds(authzConfig);

        reuseGrants = authzConfig.getValueAsBoolean(STRIMZI_AUTHORIZATION_REUSE_GRANTS, true);

        includeAcceptHeader = ConfigUtil.getDefaultBooleanConfigWithFallbackLookup(authzConfig, STRIMZI_OAUTH_INCLUDE_ACCEPT_HEADER, Config.OAUTH_INCLUDE_ACCEPT_HEADER, true);
        configureHttpRetries(authzConfig);

        configureMetrics(authzConfig);
    }

    /**
     * When a new instance of the Configuration is created some configuration options may generate warnings.
     * Use this method to print those warning to the log.
     * <p>
     * This method decouples configuration creation from logging warnings.
     */
    public void printLogs() {
        for (Log line: logs) {
            if (line.level == Log.Level.WARNING) {
                log.warn(line.message);
            } else {
                log.debug(line.message);
            }
        }
    }

    private int configureGrantsMaxIdleTimeSeconds(AuthzConfig config) {
        int grantsMaxIdleTimeSeconds = config.getValueAsInt(STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS, 300);
        if (grantsMaxIdleTimeSeconds <= 0) {
            logs.add(new Log(Log.Level.WARNING, "'" + STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS + "' set to invalid value: " + grantsMaxIdleTimeSeconds + " (should be a positive number), using the default value: 300 seconds"));
            grantsMaxIdleTimeSeconds = 300;
        }
        return grantsMaxIdleTimeSeconds;
    }

    private int configureGcPeriodSeconds(AuthzConfig config) {
        int gcPeriodSeconds = config.getValueAsInt(STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS, 300);
        if (gcPeriodSeconds <= 0) {
            logs.add(new Log(Log.Level.WARNING, "'" + STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS + "' set to invalid value: " + gcPeriodSeconds + ", using the default value: 300 seconds"));
            gcPeriodSeconds = 300;
        }
        return gcPeriodSeconds;
    }

    private void configureSuperUsers(Map<String, ?> configs) {
        String users = (String) configs.get("super.users");
        if (users != null) {
            superUsers = Arrays.stream(users.split(";"))
                    .map(UserSpec::of)
                    .collect(Collectors.toList());
        }
    }

    private void configureSSLFactory(AuthzConfig config) {
        truststore = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION, OAUTH_SSL_TRUSTSTORE_LOCATION);
        truststoreData = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES, OAUTH_SSL_TRUSTSTORE_CERTIFICATES);
        truststorePassword = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD, OAUTH_SSL_TRUSTSTORE_PASSWORD);
        truststoreType = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE, OAUTH_SSL_TRUSTSTORE_TYPE);
        prng = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION, OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION);
    }

    private void configureHostnameVerifier(AuthzConfig config) {
        String hostCheck = ConfigUtil.getConfigWithFallbackLookup(config,
                STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM, OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM);

        if (hostCheck == null) {
            hostCheck = "HTTPS";
        }
        this.certificateHostCheckAlgorithm = hostCheck;
    }
    private void configureHttpRetries(AuthzConfig config) {
        httpRetries = config.getValueAsInt(STRIMZI_AUTHORIZATION_HTTP_RETRIES, 0);
        if (httpRetries < 0) {
            throw new ConfigException("Invalid value of '" + STRIMZI_AUTHORIZATION_HTTP_RETRIES + "': " + httpRetries + ". Has to be >= 0.");
        }
    }
    private void configureMetrics(AuthzConfig config) {

        String enableMetricsString = ConfigUtil.getConfigWithFallbackLookup(config, STRIMZI_AUTHORIZATION_ENABLE_METRICS, OAUTH_ENABLE_METRICS);
        try {
            enableMetrics = enableMetricsString != null && isTrue(enableMetricsString);
        } catch (Exception e) {
            throw new ConfigException("Bad boolean value for key: " + STRIMZI_AUTHORIZATION_ENABLE_METRICS + ", value: " + enableMetricsString);
        }
    }

    private void configureTokenEndpoint(AuthzConfig config) {
        String endpoint = ConfigUtil.getConfigWithFallbackLookup(config, STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
                OAUTH_TOKEN_ENDPOINT_URI);
        if (endpoint == null) {
            throw new ConfigException("OAuth2 Token Endpoint ('" + STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI + "') not set.");
        }

        try {
            tokenEndpointUrl = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new ConfigException("Specified token endpoint uri is invalid: " + endpoint);
        }
    }

    private void configureHttpTimeouts(AuthzConfig config) {
        List<String> warnings = new LinkedList<>();
        connectTimeoutSeconds = ConfigUtil.getTimeoutConfigWithFallbackLookup(config, STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS, OAUTH_CONNECT_TIMEOUT_SECONDS, warnings);
        readTimeoutSeconds = ConfigUtil.getTimeoutConfigWithFallbackLookup(config, STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS, OAUTH_READ_TIMEOUT_SECONDS, warnings);

        for (String message: warnings) {
            logs.add(new Log(Log.Level.WARNING, message));
        }
    }

    /**
     * This method extracts the key=value configuration entries relevant for KeycloakRBACAuthorizer from
     * Kafka properties configuration file (server.properties) and wraps them with AuthzConfig instance.
     * <p>
     * Any new config options have to be added here in order to become visible, otherwise they will be ignored.
     *
     * @param configs Kafka configs map
     * @return Config object
     */
    static AuthzConfig convertToCommonConfig(Map<String, ?> configs) {
        Properties p = new Properties();

        // If you add a new config property, make sure to add it to this list
        // otherwise it won't be picked
        String[] keys = {
            STRIMZI_AUTHORIZATION_GRANTS_REFRESH_PERIOD_SECONDS,
            STRIMZI_AUTHORIZATION_GRANTS_REFRESH_POOL_SIZE,
            STRIMZI_AUTHORIZATION_GRANTS_MAX_IDLE_TIME_SECONDS,
            STRIMZI_AUTHORIZATION_GRANTS_GC_PERIOD_SECONDS,
            STRIMZI_AUTHORIZATION_HTTP_RETRIES,
            STRIMZI_AUTHORIZATION_REUSE_GRANTS,
            STRIMZI_AUTHORIZATION_DELEGATE_TO_KAFKA_ACL,
            STRIMZI_AUTHORIZATION_KAFKA_CLUSTER_NAME,
            STRIMZI_AUTHORIZATION_CLIENT_ID,
            Config.OAUTH_CLIENT_ID,
            STRIMZI_AUTHORIZATION_TOKEN_ENDPOINT_URI,
            OAUTH_TOKEN_ENDPOINT_URI,
            STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_LOCATION,
            OAUTH_SSL_TRUSTSTORE_LOCATION,
            STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_CERTIFICATES,
            OAUTH_SSL_TRUSTSTORE_CERTIFICATES,
            STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_PASSWORD,
            OAUTH_SSL_TRUSTSTORE_PASSWORD,
            STRIMZI_AUTHORIZATION_SSL_TRUSTSTORE_TYPE,
            OAUTH_SSL_TRUSTSTORE_TYPE,
            STRIMZI_AUTHORIZATION_SSL_SECURE_RANDOM_IMPLEMENTATION,
            OAUTH_SSL_SECURE_RANDOM_IMPLEMENTATION,
            STRIMZI_AUTHORIZATION_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            OAUTH_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM,
            STRIMZI_AUTHORIZATION_CONNECT_TIMEOUT_SECONDS,
            OAUTH_CONNECT_TIMEOUT_SECONDS,
            STRIMZI_AUTHORIZATION_READ_TIMEOUT_SECONDS,
            OAUTH_READ_TIMEOUT_SECONDS,
            STRIMZI_AUTHORIZATION_ENABLE_METRICS,
            OAUTH_ENABLE_METRICS,
            STRIMZI_OAUTH_INCLUDE_ACCEPT_HEADER,
            Config.OAUTH_INCLUDE_ACCEPT_HEADER
        };

        // Copy over the keys
        for (String key: keys) {
            ConfigUtil.putIfNotNull(p, key, configs.get(key));
        }

        return new AuthzConfig(p);
    }

    AuthzConfig convertToAuthzConfig(Map<String, ?> configs) {
        AuthzConfig config = Configuration.convertToCommonConfig(configs);
        isKRaft = detectKRaft(configs);
        if (isKRaft) {
            logs.add(new Log(Log.Level.DEBUG, "Detected KRaft mode ('process.roles' configured)"));
        }
        return config;
    }

    private boolean detectKRaft(Map<String, ?> configs) {
        // auto-detect KRaft mode
        Object prop = configs.get("process.roles");
        String processRoles = prop != null ? String.valueOf(prop) : null;
        return processRoles != null && processRoles.length() > 0;
    }

    boolean isKRaft() {
        return isKRaft;
    }

    String getTruststore() {
        return truststore;
    }

    String getTruststoreData() {
        return truststoreData;
    }

    String getTruststorePassword() {
        return truststorePassword;
    }

    String getTruststoreType() {
        return truststoreType;
    }

    String getPrng() {
        return prng;
    }

    String getCertificateHostCheckAlgorithm() {
        return certificateHostCheckAlgorithm;
    }

    boolean isDelegateToKafkaACL() {
        return delegateToKafkaACL;
    }

    URI getTokenEndpointUrl() {
        return tokenEndpointUrl;
    }

    String getClientId() {
        return clientId;
    }

    boolean isReuseGrants() {
        return reuseGrants;
    }

    String getClusterName() {
        return clusterName;
    }

    int getGrantsRefreshPeriodSeconds() {
        return grantsRefreshPeriodSeconds;
    }

    int getGrantsMaxIdleTimeSeconds() {
        return grantsMaxIdleTimeSeconds;
    }

    int getGrantsRefreshPoolSize() {
        return grantsRefreshPoolSize;
    }

    int getGcPeriodSeconds() {
        return gcPeriodSeconds;
    }

    List<UserSpec> getSuperUsers() {
        return superUsers;
    }

    int getHttpRetries() {
        return httpRetries;
    }

    boolean isEnableMetrics() {
        return enableMetrics;
    }

    int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    Map<String, ?> getConfigMap() {
        return configMap;
    }

    boolean includeAcceptHeader() {
        return includeAcceptHeader;
    }

    private static class Log {
        Level level;
        String message;

        Log(Level level, String message) {
            if (level == null) {
                throw new IllegalArgumentException("level is null");
            }
            this.level = level;
            this.message = message;
        }

        enum Level {
            WARNING,
            DEBUG
        }
    }

    @SuppressWarnings({"checkstyle:CyclomaticComplexity"})
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Configuration that = (Configuration) o;
        return reuseGrants == that.reuseGrants &&
                delegateToKafkaACL == that.delegateToKafkaACL &&
                grantsRefreshPeriodSeconds == that.grantsRefreshPeriodSeconds &&
                grantsMaxIdleTimeSeconds == that.grantsMaxIdleTimeSeconds &&
                grantsRefreshPoolSize == that.grantsRefreshPoolSize &&
                gcPeriodSeconds == that.gcPeriodSeconds &&
                isKRaft == that.isKRaft &&
                httpRetries == that.httpRetries &&
                enableMetrics == that.enableMetrics &&
                connectTimeoutSeconds == that.connectTimeoutSeconds &&
                readTimeoutSeconds == that.readTimeoutSeconds &&
                includeAcceptHeader == that.includeAcceptHeader &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(clusterName, that.clusterName) &&
                Objects.equals(truststore, that.truststore) &&
                Objects.equals(truststoreData, that.truststoreData) &&
                Objects.equals(truststorePassword, that.truststorePassword) &&
                Objects.equals(truststoreType, that.truststoreType) &&
                Objects.equals(prng, that.prng) &&
                Objects.equals(certificateHostCheckAlgorithm, that.certificateHostCheckAlgorithm) &&
                Objects.equals(superUsers, that.superUsers) &&
                Objects.equals(tokenEndpointUrl, that.tokenEndpointUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reuseGrants,
                clientId,
                clusterName,
                delegateToKafkaACL,
                grantsRefreshPeriodSeconds,
                grantsMaxIdleTimeSeconds,
                grantsRefreshPoolSize,
                gcPeriodSeconds,
                isKRaft,
                truststore,
                truststoreData,
                truststorePassword,
                truststoreType,
                prng,
                certificateHostCheckAlgorithm,
                superUsers,
                httpRetries,
                enableMetrics,
                tokenEndpointUrl,
                connectTimeoutSeconds,
                readTimeoutSeconds,
                includeAcceptHeader);
    }
}
