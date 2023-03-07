/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * This class represents parsed Keycloak Authorization Services grants as returned by the token endpoint
 */
public class ScopesSpec {

    /**
     * Keycloak Authorization Services scope. It aligns with all the possible authorization actions that Kafka understands
     */
    public enum AuthzScope {
        /**
         * CREATE
         */
        CREATE,

        /**
         * READ
         */
        READ,

        /**
         * WRITE
         */
        WRITE,

        /**
         * DELETE
         */
        DELETE,

        /**
         * ALTER
         */
        ALTER,

        /**
         * DESCRIBE
         */
        DESCRIBE,

        /**
         * ALTER_CONFIGS or ALTERCONFIGS
         */
        ALTER_CONFIGS,

        /**
         * DESCRIBE_CONFIGS or DESCRIBECONFIGS
         */
        DESCRIBE_CONFIGS,

        /**
         * CLUSTER_ACTION or CLUSTERACTION
         */
        CLUSTER_ACTION,

        /**
         * IDEMPOTENT_WRITE or IDEMPOTENTWRITE
         */
        IDEMPOTENT_WRITE;

        /**
         * Get a AuthzScope enum value corresponding to the passed grant
         *
         * @param grantValue Grant as a string
         * @return AuthzScope enum value
         */
        public static AuthzScope of(String grantValue) {
            final String value = grantValue.toUpperCase(Locale.ROOT);
            switch (value) {
                case "ALTERCONFIGS":
                    return ALTER_CONFIGS;
                case "DESCRIBECONFIGS":
                    return DESCRIBE_CONFIGS;
                case "CLUSTERACTION":
                    return CLUSTER_ACTION;
                case "IDEMPOTENTWRITE":
                    return IDEMPOTENT_WRITE;
                default:
                    return AuthzScope.valueOf(value);
            }
        }
    }

    private final EnumSet<AuthzScope> granted;

    private ScopesSpec(EnumSet<AuthzScope> grants) {
        this.granted = grants;
    }


    static ScopesSpec of(List<AuthzScope> scopes) {
        return new ScopesSpec(EnumSet.copyOf(scopes));
    }

    /**
     * See if the specific operation is granted based on the list of grants contained in this instance of <code>ScopesSpec</code>.
     *
     * @param operation Operation (permission) name
     * @return True if operation is granted
     */
    public boolean isGranted(String operation) {
        AuthzScope scope = AuthzScope.valueOf(operation);
        return granted.contains(scope);
    }

    @Override
    public String toString() {
        return String.valueOf(granted);
    }
}
