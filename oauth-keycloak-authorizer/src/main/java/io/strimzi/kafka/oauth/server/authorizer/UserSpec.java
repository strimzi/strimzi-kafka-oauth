/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

public class UserSpec {

    private final String type;
    private final String name;

    private UserSpec(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }


    public static UserSpec of(String principal) {
        int pos = principal.indexOf(':');
        if (pos <= 0) {
            throw new IllegalArgumentException("Invalid user specification: " + principal);
        }
        return new UserSpec(principal.substring(0, pos), principal.substring(pos + 1));
    }

    public String toString() {
        return super.toString() + " " + type + ":" + name;
    }
}
