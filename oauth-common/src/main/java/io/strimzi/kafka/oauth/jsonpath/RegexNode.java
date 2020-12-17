package io.strimzi.kafka.oauth.jsonpath;

import java.util.regex.Pattern;

public class RegexNode extends Node {

    private final Pattern pattern;

    RegexNode(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    public Pattern getPattern() {
        return pattern;
    }
}
