/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.Collections;
import java.util.List;

class ComposedPredicateNode extends AbstractPredicateNode {

    private final List<ExpressionNode> expressions;

    ComposedPredicateNode(List<ExpressionNode> expressions) {
        this.expressions = Collections.unmodifiableList(expressions);
    }

    public List<ExpressionNode> getExpressions() {
        return expressions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ExpressionNode node: expressions) {
            sb.append(node.getOp() != null ? " " + node.getOp() + " " : "");
            if (node.getPredicate() instanceof ComposedPredicateNode) {
                sb.append(" (").append(node.getPredicate()).append(")");
            } else {
                sb.append(node.getPredicate());
            }
        }
        return sb.toString();
    }
}
