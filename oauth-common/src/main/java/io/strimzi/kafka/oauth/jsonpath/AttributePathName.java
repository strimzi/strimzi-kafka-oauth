/*
 * Copyright 2017-2019, Strimzi authors.
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
        private final boolean deep;

        public Segment(String name, boolean deep) {
            this.name = name;
            this.deep = deep;
        }

        public String name() {
            return name;
        }

        public boolean deep() {
            return deep;
        }
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            sb.append(s.deep ? ".." : ".");
            sb.append(s.name);
        }
        return sb.toString();
    }
}
