/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.auth;

import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.junit.Assert;

public class ErrorReportingWithDetailsTests extends ErrorReportingTests {

    @Override
    String getKafkaBootstrap(int port) {
        return "kafka:" + (port - 100);
    }

    @Override
    void commonChecks(Throwable cause) {
        String message = cause.toString();
        Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
        Assert.assertFalse("Error message is not sanitised", message.substring(message.length() - 16).startsWith("ErrId:"));
        Assert.assertTrue("Error message contains ErrId", message.contains("ErrId:"));
    }

    @Override
    void checkUnparseableJwtTokenErrorMessage(String message) {
        Assert.assertTrue(message.contains("Token signature validation failed"));
    }

    @Override
    void checkCorruptTokenIntrospectErrorMessage(String message) {
        Assert.assertTrue(message.contains("Token has expired"));
    }

    @Override
    void checkInvalidJwtTokenKidErrorMessage(String message) {
        Assert.assertTrue(message.contains("Unknown signing key (kid:"));
    }

    @Override
    void checkForgedJwtSigErrorMessage(String message) {
        Assert.assertTrue(message.contains("Invalid token signature"));
    }

    @Override
    void checkForgedJwtSigIntrospectErrorMessage(String message) {
        Assert.assertTrue(message.contains("Token has expired"));
    }

    @Override
    void checkExpiredJwtTokenErrorMessage(String message) {
        Assert.assertTrue(message.contains("Token expired at: "));
    }

    @Override
    void checkBadClientIdOAuthOverPlainErrorMessage(String message) {
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkBadCSecretOAuthOverPlainErrorMessage(String message) {
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkCantConnectPlainWithClientCredentialsErrorMessage(String message) {
        Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkCantConnectIntrospectErrorMessage(String message) {
        Assert.assertTrue(message.contains("Connection refused"));
    }
}
