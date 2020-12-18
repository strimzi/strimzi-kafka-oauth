/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

import java.util.List;

class AttributePathName {

    private final List<Segment> segments;

    AttributePathName(List<Segment> segments) {
        this.segments = segments;
    }

    public static class Segment {

        private final String name;

        public Segment(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            sb.append(".").append(s.name);
        }
        return sb.toString();
    }
}
