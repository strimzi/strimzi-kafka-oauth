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
        return "kafka:" + port;
    }

    @Override
    void commonChecks(Throwable cause) {
        Assert.assertEquals("Expected SaslAuthenticationException", SaslAuthenticationException.class, cause.getClass());
    }

    @Override
    void checkErrId(String message) {
        Assert.assertFalse("Error message is not sanitised", message.substring(message.length() - 16).startsWith("ErrId:"));
        Assert.assertTrue("Error message contains ErrId", message.contains("ErrId:"));
    }

    @Override
    void checkUnparseableJwtTokenErrorMessage(String message) {
        super.checkUnparseableJwtTokenErrorMessage(message);
        //Assert.assertTrue(message.contains("Parsing error"));
    }

    @Override
    void checkCorruptTokenIntrospectErrorMessage(String message) {
        super.checkCorruptTokenIntrospectErrorMessage(message);
        //Assert.assertTrue(message.contains("Token not active"));
    }

    @Override
    void checkInvalidJwtTokenKidErrorMessage(String message) {
        super.checkInvalidJwtTokenKidErrorMessage(message);
        //Assert.assertTrue(message.contains("Unknown signing key (kid:"));
    }

    @Override
    void checkForgedJwtSigErrorMessage(String message) {
        super.checkForgedJwtSigErrorMessage(message);
        //Assert.assertTrue(message.contains("Invalid token signature"));
    }

    @Override
    void checkForgedJwtSigIntrospectErrorMessage(String message) {
        super.checkForgedJwtSigIntrospectErrorMessage(message);
        //Assert.assertTrue(message.contains("Token has expired"));
    }

    @Override
    void checkExpiredJwtTokenErrorMessage(String message) {
        super.checkExpiredJwtTokenErrorMessage(message);
        //Assert.assertTrue(message.contains("Token expired at: "));
    }

    @Override
    void checkBadClientIdOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        super.checkBadClientIdOAuthOverPlainErrorMessage(message);
        //Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkBadCSecretOAuthOverPlainErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        super.checkBadCSecretOAuthOverPlainErrorMessage(message);
        //Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkCantConnectPlainWithClientCredentialsErrorMessage(String message) {
        // errId can not be propagated over PLAIN so it is not present
        super.checkCantConnectPlainWithClientCredentialsErrorMessage(message);
        //Assert.assertTrue(message.contains("credentials for user could not be verified"));
    }

    @Override
    void checkCantConnectIntrospectErrorMessage(String message) {
        super.checkCantConnectIntrospectErrorMessage(message);
        Assert.assertTrue(message.contains("Connection refused"));
    }
}
