/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class BooleanEvaluator {
    boolean current = true;

    boolean update(Logical op, boolean val) {
        if (op == null || op == Logical.AND) {
            return and(val);
        }
        if (op == Logical.OR) {
            return or(val);
        }
        throw new IllegalArgumentException("Unsupported logical operator: " + op);
    }

    boolean and(boolean val) {
        current = current && val;
        return current;
    }

    boolean or(boolean val) {
        current = current || val;
        return current;
    }
}
