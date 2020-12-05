/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class PredicateNode extends Node {
    private final Node lval;
    private final OperatorNode op;
    private final Node rval;

    PredicateNode(Node lval, OperatorNode op, Node rval) {
        this.lval = lval;
        this.op = op;
        this.rval = rval;
    }

    public Node getLval() {
        return lval;
    }

    public OperatorNode getOp() {
        return op;
    }

    public Node getRval() {
        return rval;
    }

    @Override
    public String toString() {
        return "" + lval + " " + op + (rval != null ? " " + rval : "");
    }
}
