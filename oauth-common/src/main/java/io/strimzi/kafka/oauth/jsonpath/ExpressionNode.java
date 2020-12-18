/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class ExpressionNode extends Node {
    private final Logical op;
    private final boolean negated;
    private final AbstractPredicateNode predicate;

    ExpressionNode(Logical op, boolean negated, AbstractPredicateNode predicate) {
        this.op = op;
        this.negated = negated;
        this.predicate = predicate;
    }

    public Logical getOp() {
        return op;
    }

    public AbstractPredicateNode getPredicate() {
        return predicate;
    }

    public boolean isNegated() {
        return negated;
    }

    @Override
    public String toString() {
        return "" + (op != null ? op : "") + " " + (negated ? "!" : "") + predicate;
    }

}
