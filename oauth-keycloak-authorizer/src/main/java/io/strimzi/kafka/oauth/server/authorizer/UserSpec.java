/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

public class UserSpec {

    private String type;
    private String name;

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
        String[] spec = principal.split(":");
        return new UserSpec(spec[0], spec[1]);
    }

    public String toString() {
        return super.toString() + type + ":" + name;
    }
}
