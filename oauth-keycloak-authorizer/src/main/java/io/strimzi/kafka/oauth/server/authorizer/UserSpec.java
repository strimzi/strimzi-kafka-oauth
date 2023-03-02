/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

/**
 * A class used to hold parsed superusers specs
 */
public class UserSpec {

    private final String type;
    private final String name;

    /**
     * Create a new instance
     *
     * @param type A principal type
     * @param name A principal name
     */
    private UserSpec(String type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Get the type
     *
     * @return The type as a string
     */
    public String getType() {
        return type;
    }

    /**
     * Get the name
     *
     * @return The name
     */
    public String getName() {
        return name;
    }


    /**
     * Factory method to parse a <code>UserSpec</code> instance from a string
     *
     * @param principal A principal as a string
     * @return A new UserSpec instance
     */
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
