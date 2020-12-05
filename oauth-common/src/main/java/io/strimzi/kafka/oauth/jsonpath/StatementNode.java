/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class StatementNode extends Node {
    List<ExpressionNode> expressions = new ArrayList<>();

    public void add(ExpressionNode expression) {
        expressions.add(expression);
    }

    @Override
    public String toString() {
        return expressions.size() == 0 ? "" : expressions.stream().map(ExpressionNode::toString).collect(Collectors.joining(" ")).substring(1);
    }
}
