/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class ExpressionNode extends Node {
    private final Logical op;
    private final PredicateNode predicate;

    ExpressionNode(Logical op, PredicateNode predicate) {
        this.op = op;
        this.predicate = predicate;
    }

    public Logical getOp() {
        return op;
    }

    public PredicateNode getPredicate() {
        return predicate;
    }

    @Override
    public String toString() {
        return "" + (op != null ? op : "") + " " + predicate;
    }

}
