/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class ScopesSpec {

    public enum AuthzScope {
        CREATE,
        READ,
        WRITE,
        DELETE,
        ALTER,
        DESCRIBE,
        ALTER_CONFIGS,
        DESCRIBE_CONFIGS,
        CLUSTER_ACTION,
        IDEMPOTENT_WRITE;

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

    public boolean isGranted(String operation) {
        AuthzScope scope = AuthzScope.valueOf(operation);
        return granted.contains(scope);
    }

    @Override
    public String toString() {
        return String.valueOf(granted);
    }
}
