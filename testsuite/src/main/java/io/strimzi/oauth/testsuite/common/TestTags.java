/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.common;

/**
 * Constants for JUnit {@code @Tag} values used across the test suite.
 */
public final class TestTags {

    public static final String ACL = "acl";
    public static final String AUTH = "auth";
    public static final String AUDIENCE = "audience";
    public static final String AUTHENTICATION = "authentication";
    public static final String AUTHORIZATION = "authorization";
    public static final String CLIENT = "client";
    public static final String CLIENT_ASSERTION = "client-assertion";
    public static final String CONCURRENCY = "concurrency";
    public static final String CONCURRENT = "concurrent";
    public static final String CONFIG = "config";
    public static final String CONFIGURATION = "configuration";
    public static final String CUSTOM_CHECK = "custom-check";
    public static final String ECDSA = "ecdsa";
    public static final String ERROR_HANDLING = "error-handling";
    public static final String FLOOD = "flood";
    public static final String GRANTS = "grants";
    public static final String GROUPS = "groups";
    public static final String HTTP = "http";
    public static final String INTROSPECTION = "introspection";
    public static final String JWT = "jwt";
    public static final String JWKS = "jwks";
    public static final String MANIPULATION = "manipulation";
    public static final String METRICS = "metrics";
    public static final String MULTI_SASL = "multi-sasl";
    public static final String PASSWORD_GRANT = "password-grant";
    public static final String PEM = "pem";
    public static final String PERFORMANCE = "performance";
    public static final String PKCS12 = "pkcs12";
    public static final String PLAIN = "plain";
    public static final String REFRESH = "refresh";
    public static final String RETRIES = "retries";
    public static final String RETRY = "retry";
    public static final String RSA = "rsa";
    public static final String SCRAM = "scram";
    public static final String SINGLETON = "singleton";
    public static final String TIMEOUT = "timeout";

    private TestTags() {
    }
}