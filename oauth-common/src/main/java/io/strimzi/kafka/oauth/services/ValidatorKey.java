/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import java.util.Objects;

public class ValidatorKey {

    private final String validIssuerUri;
    private final String audience;
    private final String customClaimCheck;
    private final String usernameClaim;
    private final String fallbackUsernameClaim;
    private final String fallbackUsernamePrefix;
    private final String groupQuery;
    private final String groupDelimiter;

    private final String sslTruststore;
    private final String sslStorePassword;
    private final String sslStoreType;
    private final String sslRandom;
    private final boolean hasHostnameVerifier;

    private final int connectTimeout;
    private final int readTimeout;

    ValidatorKey(String validIssuerUri,
            String audience,
            String customClaimCheck,
            String usernameClaim,
            String fallbackUsernameClaim,
            String fallbackUsernamePrefix,
            String groupQuery,
            String groupDelimiter,
            String sslTruststore,
            String sslStorePassword,
            String sslStoreType,
            String sslRandom,
            boolean hasHostnameVerifier,
            int connectTimeout,
            int readTimeout) {

        this.validIssuerUri = validIssuerUri;
        this.audience = audience;
        this.customClaimCheck = customClaimCheck;
        this.usernameClaim = usernameClaim;
        this.fallbackUsernameClaim = fallbackUsernameClaim;
        this.fallbackUsernamePrefix = fallbackUsernamePrefix;
        this.groupQuery = groupQuery;
        this.groupDelimiter = groupDelimiter;
        this.sslTruststore = sslTruststore;
        this.sslStorePassword = sslStorePassword;
        this.sslStoreType = sslStoreType;
        this.sslRandom = sslRandom;
        this.hasHostnameVerifier = hasHostnameVerifier;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatorKey)) return false;
        ValidatorKey that = (ValidatorKey) o;
        return hasHostnameVerifier == that.hasHostnameVerifier &&
                Objects.equals(validIssuerUri, that.validIssuerUri) &&
                Objects.equals(audience, that.audience) &&
                Objects.equals(customClaimCheck, that.customClaimCheck) &&
                Objects.equals(usernameClaim, that.usernameClaim) &&
                Objects.equals(fallbackUsernameClaim, that.fallbackUsernameClaim) &&
                Objects.equals(fallbackUsernamePrefix, that.fallbackUsernamePrefix) &&
                Objects.equals(groupQuery, that.groupQuery) &&
                Objects.equals(groupDelimiter, that.groupDelimiter) &&
                Objects.equals(sslTruststore, that.sslTruststore) &&
                Objects.equals(sslStorePassword, that.sslStorePassword) &&
                Objects.equals(sslStoreType, that.sslStoreType) &&
                Objects.equals(sslRandom, that.sslRandom) &&
                Objects.equals(connectTimeout, that.connectTimeout) &&
                Objects.equals(readTimeout, that.readTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validIssuerUri, audience, customClaimCheck, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, groupQuery, groupDelimiter, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier, connectTimeout, readTimeout);
    }


    public static class JwtValidatorKey extends ValidatorKey {

        private final String jwksEndpointUri;
        private final int jwksRefreshSeconds;
        private final int jwksExpirySeconds;
        private final int jwksRefreshMinPauseSeconds;
        private final boolean checkAccessTokenType;

        @SuppressWarnings("checkstyle:parameternumber")
        public JwtValidatorKey(String validIssuerUri,
                               String audience,
                               String customClaimCheck,
                               String usernameClaim,
                               String fallbackUsernameClaim,
                               String fallbackUsernamePrefix,
                               String groupQuery,
                               String groupDelimiter,
                               String sslTruststore,
                               String sslStorePassword,
                               String sslStoreType,
                               String sslRandom,
                               boolean hasHostnameVerifier,

                               String jwksEndpointUri,
                               int jwksRefreshSeconds,
                               int jwksExpirySeconds,
                               int jwksRefreshMinPauseSeconds,
                               boolean checkAccessTokenType,
                               int connectTimeout,
                               int readTimeout) {

            super(validIssuerUri, audience, customClaimCheck, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, groupQuery, groupDelimiter, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier, connectTimeout, readTimeout);
            this.jwksEndpointUri = jwksEndpointUri;
            this.jwksRefreshSeconds = jwksRefreshSeconds;
            this.jwksExpirySeconds = jwksExpirySeconds;
            this.jwksRefreshMinPauseSeconds = jwksRefreshMinPauseSeconds;
            this.checkAccessTokenType = checkAccessTokenType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            JwtValidatorKey that = (JwtValidatorKey) o;
            return jwksRefreshSeconds == that.jwksRefreshSeconds &&
                    jwksExpirySeconds == that.jwksExpirySeconds &&
                    jwksRefreshMinPauseSeconds == that.jwksRefreshMinPauseSeconds &&
                    checkAccessTokenType == that.checkAccessTokenType &&
                    Objects.equals(jwksEndpointUri, that.jwksEndpointUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), jwksEndpointUri, jwksRefreshSeconds, jwksExpirySeconds, jwksRefreshMinPauseSeconds, checkAccessTokenType);
        }
    }

    public static class IntrospectionValidatorKey extends ValidatorKey {

        private final String introspectionEndpoint;
        private final String userInfoEndpoint;
        private final String validTokenType;
        private final String clientId;
        private final String clientSecret;

        @SuppressWarnings("checkstyle:parameternumber")
        public IntrospectionValidatorKey(String validIssuerUri,
                                  String audience,
                                  String customClaimCheck,
                                  String usernameClaim,
                                  String fallbackUsernameClaim,
                                  String fallbackUsernamePrefix,
                                  String groupQuery,
                                  String groupDelimiter,
                                  String sslTruststore,
                                  String sslStorePassword,
                                  String sslStoreType,
                                  String sslRandom,
                                  boolean hasHostnameVerifier,

                                  String introspectionEndpoint,
                                  String userInfoEndpoint,
                                  String validTokenType,
                                  String clientId,
                                  String clientSecret,
                                  int connectTimeout,
                                  int readTimeout) {

            super(validIssuerUri, audience, customClaimCheck, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, groupQuery, groupDelimiter, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier, connectTimeout, readTimeout);
            this.introspectionEndpoint = introspectionEndpoint;
            this.userInfoEndpoint = userInfoEndpoint;
            this.validTokenType = validTokenType;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            IntrospectionValidatorKey that = (IntrospectionValidatorKey) o;
            return Objects.equals(introspectionEndpoint, that.introspectionEndpoint) &&
                    Objects.equals(userInfoEndpoint, that.userInfoEndpoint) &&
                    Objects.equals(validTokenType, that.validTokenType) &&
                    Objects.equals(clientId, that.clientId) &&
                    Objects.equals(clientSecret, that.clientSecret);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), introspectionEndpoint, userInfoEndpoint, validTokenType, clientId, clientSecret);
        }
    }
}
