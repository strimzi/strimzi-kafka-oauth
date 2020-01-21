/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import java.util.EnumSet;
import java.util.List;

public class ScopesSpec {

    public enum AuthzScope {
        Create,
        Read,
        Write,
        Delete,
        Alter,
        Describe,
        AlterConfigs,
        DescribeConfigs,
        ClusterAction,
        IdempotentWrite
    }

    private EnumSet<AuthzScope> granted;

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
