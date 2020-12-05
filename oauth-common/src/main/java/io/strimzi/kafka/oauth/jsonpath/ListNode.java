/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.ArrayList;
import java.util.stream.Collectors;

class ListNode extends Node {
    private final ArrayList<Node> items;

    ListNode(ArrayList<Node> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return items.stream().map(Object::toString).collect(Collectors.joining(",", "[", "]"));
    }

    public boolean contains(Object o) {
        for (Node n : items) {
            if (n.equals(o)) {
                return true;
            }
        }
        return false;
    }
}
