/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.services;

import java.util.Objects;

public class ValidatorKey {

    private final String validIssuerUri;
    private final String audience;
    private final String usernameClaim;
    private final String fallbackUsernameClaim;
    private final String fallbackUsernamePrefix;

    private final String sslTruststore;
    private final String sslStorePassword;
    private final String sslStoreType;
    private final String sslRandom;
    private final boolean hasHostnameVerifier;

    ValidatorKey(String validIssuerUri,
            String audience,
            String usernameClaim,
            String fallbackUsernameClaim,
            String fallbackUsernamePrefix,
            String sslTruststore,
            String sslStorePassword,
            String sslStoreType,
            String sslRandom,
            boolean hasHostnameVerifier) {

        this.validIssuerUri = validIssuerUri;
        this.audience = audience;
        this.usernameClaim = usernameClaim;
        this.fallbackUsernameClaim = fallbackUsernameClaim;
        this.fallbackUsernamePrefix = fallbackUsernamePrefix;
        this.sslTruststore = sslTruststore;
        this.sslStorePassword = sslStorePassword;
        this.sslStoreType = sslStoreType;
        this.sslRandom = sslRandom;
        this.hasHostnameVerifier = hasHostnameVerifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatorKey)) return false;
        ValidatorKey that = (ValidatorKey) o;
        return hasHostnameVerifier == that.hasHostnameVerifier &&
                Objects.equals(validIssuerUri, that.validIssuerUri) &&
                Objects.equals(audience, that.audience) &&
                Objects.equals(usernameClaim, that.usernameClaim) &&
                Objects.equals(fallbackUsernameClaim, that.fallbackUsernameClaim) &&
                Objects.equals(fallbackUsernamePrefix, that.fallbackUsernamePrefix) &&
                Objects.equals(sslTruststore, that.sslTruststore) &&
                Objects.equals(sslStorePassword, that.sslStorePassword) &&
                Objects.equals(sslStoreType, that.sslStoreType) &&
                Objects.equals(sslRandom, that.sslRandom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validIssuerUri, audience, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier);
    }


    public static class JwtValidatorKey extends ValidatorKey {

        private final String jwksEndpointUri;
        private final int jwksRefreshSeconds;
        private final int jwksExpirySeconds;
        private final int jwksRefreshMinPauseSeconds;
        private final boolean checkAccessTokenType;
        private final boolean enableBouncy;
        private final int bouncyPosition;

        @SuppressWarnings("checkstyle:parameternumber")
        public JwtValidatorKey(String validIssuerUri,
                        String audience,
                        String usernameClaim,
                        String fallbackUsernameClaim,
                        String fallbackUsernamePrefix,
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
                        boolean enableBouncy,
                        int bouncyPosition) {

            super(validIssuerUri, audience, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier);
            this.jwksEndpointUri = jwksEndpointUri;
            this.jwksRefreshSeconds = jwksRefreshSeconds;
            this.jwksExpirySeconds = jwksExpirySeconds;
            this.jwksRefreshMinPauseSeconds = jwksRefreshMinPauseSeconds;
            this.checkAccessTokenType = checkAccessTokenType;
            this.enableBouncy = enableBouncy;
            this.bouncyPosition = bouncyPosition;
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
                    enableBouncy == that.enableBouncy &&
                    bouncyPosition == that.bouncyPosition &&
                    Objects.equals(jwksEndpointUri, that.jwksEndpointUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), jwksEndpointUri, jwksRefreshSeconds, jwksExpirySeconds, jwksRefreshMinPauseSeconds, checkAccessTokenType, enableBouncy, bouncyPosition);
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
                                  String usernameClaim,
                                  String fallbackUsernameClaim,
                                  String fallbackUsernamePrefix,
                                  String sslTruststore,
                                  String sslStorePassword,
                                  String sslStoreType,
                                  String sslRandom,
                                  boolean hasHostnameVerifier,

                                  String introspectionEndpoint,
                                  String userInfoEndpoint,
                                  String validTokenType,
                                  String clientId,
                                  String clientSecret) {

            super(validIssuerUri, audience, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier);
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
