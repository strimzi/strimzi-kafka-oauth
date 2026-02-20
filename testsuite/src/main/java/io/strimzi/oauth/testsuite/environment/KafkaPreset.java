/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

/**
 * Enum representing the Kafka listener configuration presets for test environments.
 * Each preset defines a specific set of listeners and their OAuth configuration.
 */
public enum KafkaPreset {

    /** No Kafka container */
    NONE,

    /** MockOAuth preset: JWT, INTROSPECT, JWTPLAIN, PLAIN, INTROSPECTTIMEOUT, FAILINGINTROSPECT, FAILINGJWT listeners */
    MOCK_OAUTH,

    /** Keycloak Auth preset: JWT, INTROSPECT, AUDIENCE, AUDIENCEINTROSPECT, JWTPLAIN, INTROSPECTPLAIN, CUSTOM, CUSTOMINTROSPECT, PLAIN, SCRAM, FLOOD, JWTPLAINWITHOUTCC, FORGE listeners */
    KEYCLOAK_AUTH,

    /** Keycloak Authz preset: JWT, INTROSPECT, JWTPLAIN, INTROSPECTPLAIN, JWTREFRESH, PLAIN, SCRAM listeners + KeycloakAuthorizer */
    KEYCLOAK_AUTHZ,

    /** Keycloak Errors preset: JWT, INTROSPECT, JWTPLAIN, INTROSPECTPLAIN, EXPIRETEST, JWTCANTCONNECT, INTROSPECTCANTCONNECT, INTROSPECTTIMEOUT listeners */
    KEYCLOAK_ERRORS,

    /** Hydra preset: INTROSPECT, JWT listeners (with Hydra TLS endpoints) */
    HYDRA
}
