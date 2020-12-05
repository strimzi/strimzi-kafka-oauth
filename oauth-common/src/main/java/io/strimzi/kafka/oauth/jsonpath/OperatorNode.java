/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class OperatorNode extends Node {

    private final String name;

    static final OperatorNode EQ = new OperatorNode(Constants.EQ);
    static final OperatorNode NEQ = new OperatorNode(Constants.NEQ);
    static final OperatorNode LT = new OperatorNode(Constants.LT);
    static final OperatorNode GT = new OperatorNode(Constants.GT);
    static final OperatorNode LTE = new OperatorNode(Constants.LTE);
    static final OperatorNode GTE = new OperatorNode(Constants.GTE);
    static final OperatorNode MATCH_RE = new OperatorNode(Constants.MATCH_RE);
    static final OperatorNode IN = new OperatorNode(Constants.IN);
    static final OperatorNode NIN = new OperatorNode(Constants.NIN);
    static final OperatorNode ANYOF = new OperatorNode(Constants.ANYOF);
    static final OperatorNode NONEOF = new OperatorNode(Constants.NONEOF);

    OperatorNode(char[] token) {
        name = new String(token);
    }

    @Override
    public String toString() {
        return name;
    }
}
