/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.services;

import java.util.Objects;

public class ValidatorKey {

    String validIssuerUri;
    String usernameClaim;
    String fallbackUsernameClaim;
    String fallbackUsernamePrefix;

    String sslTruststore;
    String sslStorePassword;
    String sslStoreType;
    String sslRandom;
    boolean hasHostnameVerifier;


    ValidatorKey() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatorKey)) return false;
        ValidatorKey that = (ValidatorKey) o;
        return hasHostnameVerifier == that.hasHostnameVerifier &&
                Objects.equals(validIssuerUri, that.validIssuerUri) &&
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
        return Objects.hash(validIssuerUri, usernameClaim, fallbackUsernameClaim, fallbackUsernamePrefix, sslTruststore, sslStorePassword, sslStoreType, sslRandom, hasHostnameVerifier);
    }


    public static class JwtValidatorKey extends ValidatorKey {

        String jwksEndpointUri;
        int jwksRefreshSeconds;
        int jwksExpirySeconds;
        boolean checkAccessTokenType;
        boolean enableBouncy;
        int bouncyPosition;

        JwtValidatorKey() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            JwtValidatorKey that = (JwtValidatorKey) o;
            return jwksRefreshSeconds == that.jwksRefreshSeconds &&
                    jwksExpirySeconds == that.jwksExpirySeconds &&
                    checkAccessTokenType == that.checkAccessTokenType &&
                    enableBouncy == that.enableBouncy &&
                    bouncyPosition == that.bouncyPosition &&
                    Objects.equals(jwksEndpointUri, that.jwksEndpointUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), jwksEndpointUri, jwksRefreshSeconds, jwksExpirySeconds, checkAccessTokenType, enableBouncy, bouncyPosition);
        }
    }

    public static class IntrospectionValidatorKey extends ValidatorKey {

        String introspectionEndpoint;
        String userInfoEndpoint;
        String validTokenType;
        String clientId;
        String clientSecret;

        IntrospectionValidatorKey() {}

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





    public abstract static class Builder<T, K extends ValidatorKey> {

        K result;

        public K build() {
            return result;
        }

        abstract T getThis();

        public T validIssuerUri(String validIssuerUri) {
            result.validIssuerUri = validIssuerUri;
            return getThis();
        }

        public T usernameClaim(String usernameClaim) {
            result.usernameClaim = usernameClaim;
            return getThis();
        }

        public T fallbackUsernameClaim(String fallbackUsernameClaim) {
            result.fallbackUsernameClaim = fallbackUsernameClaim;
            return getThis();
        }

        public T fallbackUsernamePrefix(String fallbackUsernamePrefix) {
            result.fallbackUsernamePrefix = fallbackUsernamePrefix;
            return getThis();
        }

        public T sslTruststore(String sslTruststore) {
            result.sslTruststore = sslTruststore;
            return getThis();
        }

        public T sslStorePassword(String sslStorePassword) {
            result.sslStorePassword = sslStorePassword;
            return getThis();
        }

        public T sslStoreType(String sslStoreType) {
            result.sslStoreType = sslStoreType;
            return getThis();
        }

        public T sslRandom(String sslRandom) {
            result.sslRandom = sslRandom;
            return getThis();
        }

        public T hasHostnameVerifier(boolean hasHostnameVerifier) {
            result.hasHostnameVerifier = hasHostnameVerifier;
            return getThis();
        }
    }

    public static class JwtValidatorKeyBuilder extends Builder<JwtValidatorKeyBuilder, JwtValidatorKey> {

        JwtValidatorKeyBuilder() {
            result = new JwtValidatorKey();
        }

        @Override
        JwtValidatorKeyBuilder getThis() {
            return this;
        }

        public JwtValidatorKeyBuilder jwksEndpointUri(String jwksEndpointUri) {
            result.jwksEndpointUri = jwksEndpointUri;
            return getThis();
        }

        public JwtValidatorKeyBuilder jwksRefreshSeconds(int jwksRefreshSeconds) {
            result.jwksRefreshSeconds = jwksRefreshSeconds;
            return getThis();
        }

        public JwtValidatorKeyBuilder jwksExpirySeconds(int jwksExpirySeconds) {
            result.jwksExpirySeconds = jwksExpirySeconds;
            return getThis();
        }

        public JwtValidatorKeyBuilder checkAccessTokenType(boolean checkAccessTokenType) {
            result.checkAccessTokenType = checkAccessTokenType;
            return getThis();
        }

        public JwtValidatorKeyBuilder enableBouncy(boolean enableBouncy) {
            result.enableBouncy = enableBouncy;
            return getThis();
        }

        public JwtValidatorKeyBuilder bouncyPosition(int bouncyPosition) {
            result.bouncyPosition = bouncyPosition;
            return getThis();
        }
    }

    public static class IntrospectionValidatorKeyBuilder extends Builder<IntrospectionValidatorKeyBuilder, IntrospectionValidatorKey> {

        IntrospectionValidatorKeyBuilder() {
            result = new IntrospectionValidatorKey();
        }

        @Override
        IntrospectionValidatorKeyBuilder getThis() {
            return this;
        }

        public IntrospectionValidatorKeyBuilder introspectionEndpoint(String introspectionEndpoint) {
            result.introspectionEndpoint = introspectionEndpoint;
            return getThis();
        }

        public IntrospectionValidatorKeyBuilder userInfoEndpoint(String userInfoEndpoint) {
            result.userInfoEndpoint = userInfoEndpoint;
            return getThis();
        }

        public IntrospectionValidatorKeyBuilder validTokenType(String validTokenType) {
            result.validTokenType = validTokenType;
            return getThis();
        }

        public IntrospectionValidatorKeyBuilder clientId(String clientId) {
            result.clientId = clientId;
            return getThis();
        }

        public IntrospectionValidatorKeyBuilder clientSecret(String clientSecret) {
            result.clientSecret = clientSecret;
            return getThis();
        }
    }

    public static JwtValidatorKeyBuilder forJwtValidator() {
        return new JwtValidatorKeyBuilder();
    }

    public static IntrospectionValidatorKeyBuilder forIntrospectionValidator() {
        return new IntrospectionValidatorKeyBuilder();
    }
}
