/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.ArrayList;

class ListNode extends Node {
    private final ArrayList<Node> items;

    ListNode(ArrayList<Node> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return items.toString();
    }

    public boolean contains(Object o) {
        return items.contains(o);
    }
}
