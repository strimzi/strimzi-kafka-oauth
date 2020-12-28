/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

public class Constants {
    static final char SPACE = ' ';
    static final char DOT = '.';
    static final char COMMA = ',';
    static final char SINGLE = '\'';
    static final char DOUBLE = '\"';
    static final char LEFT_BRACKET = '(';
    static final char RIGHT_BRACKET = ')';
    static final char LEFT_SQUARE_BRACKET = '[';
    static final char RIGHT_SQUARE_BRACKET = ']';

    static final char[] NULL = {'n', 'u', 'l', 'l'};
    static final char[] AND = {'a', 'n', 'd'};
    static final char[] AND_SYMBOLIC = {'&', '&'};
    static final char[] OR = {'o', 'r'};
    static final char[] OR_SYMBOLIC = {'|', '|'};
    static final char[] NOT = {'!'};
    static final char[] EQ = {'=', '='};
    static final char[] NEQ = {'!', '='};
    static final char[] LT = {'<'};
    static final char[] GT = {'>'};
    static final char[] LTE = {'<', '='};
    static final char[] GTE = {'>', '='};
    static final char[] MATCH_RE = {'=', '~'};
    static final char[] IN = {'i', 'n'};
    static final char[] NIN = {'n', 'i', 'n'};
    static final char[] ANYOF = {'a', 'n', 'y', 'o', 'f'};
    static final char[] NONEOF = {'n', 'o', 'n', 'e', 'o', 'f'};
    static final int EOL = -1;
}
