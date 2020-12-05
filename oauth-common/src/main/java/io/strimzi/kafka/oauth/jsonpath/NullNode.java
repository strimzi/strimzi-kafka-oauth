/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class NullNode extends Node {
    static final NullNode INSTANCE = new NullNode();

    private NullNode() {
    }

    @Override
    public String toString() {
        return "null";
    }
}
