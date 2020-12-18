/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class PathNameNode extends Node {
    private final AttributePathName pathname;

    PathNameNode(AttributePathName pathname) {
        this.pathname = pathname;
    }

    public AttributePathName getPathname() {
        return pathname;
    }

    @Override
    public String toString() {
        return "@" + pathname.toString();
    }
}
