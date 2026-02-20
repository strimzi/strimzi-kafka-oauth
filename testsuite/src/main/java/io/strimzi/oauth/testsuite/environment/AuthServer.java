/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.environment;

/**
 * Enum representing the type of OAuth authorization server to use in tests.
 */
public enum AuthServer {

    /** No auth server (unit tests that don't need one) */
    NONE,

    /** MockOAuth container - lightweight mock server for OAuth endpoints */
    MOCK_OAUTH,

    /** Keycloak container - full-featured OAuth/OIDC server */
    KEYCLOAK,

    /** Hydra containers - ORY Hydra OAuth2 server (opaque + JWT instances) */
    HYDRA
}
