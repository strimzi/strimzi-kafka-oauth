/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.regex.Pattern;

public class RegexNode extends Node {

    private final Pattern pattern;

    RegexNode(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    RegexNode(String regex, int flags) {
        this.pattern = Pattern.compile(regex, flags);
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return pattern.pattern();
    }

    public static int applyFlag(ParsingContext ctx, char c, int current) {
        switch (c) {
            case 'i': {
                return current | Pattern.CASE_INSENSITIVE;
            }
            default:
                throw new JsonPathFilterQueryException("RegEx expression with unsupported option: " + c + " - " + ctx.toString());
        }
    }
}
