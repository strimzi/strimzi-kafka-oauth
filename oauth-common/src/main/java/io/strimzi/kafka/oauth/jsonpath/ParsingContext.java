/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

class ParsingContext implements Cloneable {

    char[] buffer;
    int start;
    int current;

    ParsingContext(char[] buffer) {
        this(buffer, 0, 0);
    }

    ParsingContext(char[] buffer, int start, int current) {
        this.buffer = buffer;
        this.start = start;
        this.current = current;
    }

    @Override
    protected ParsingContext clone() throws CloneNotSupportedException {
        ParsingContext ctx = (ParsingContext) super.clone();
        ctx.buffer = buffer;
        ctx.start = start;
        ctx.current = current;
        return ctx;
    }

    public ParsingContext copy() {
        try {
            return clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void reset() {
        current = start;
    }

    public void reset(int offset) {
        if (offset < start || offset > buffer.length) {
            throw new IllegalArgumentException("Invalid offset");
        }
        current = offset;
    }

    public int resetStart() {
        start = current;
        return start;
    }

    public int unread() {
        if (current <= buffer.length && current > 0) {
            current -= 1;
        }
        return current;
    }

    public int count() {
        return current - start;
    }

    public boolean eol() {
        return current >= buffer.length;
    }

    public int read() {
        if (current < buffer.length) {
            return buffer[current++];
        }
        return -1;
    }

    public int peek() {
        if (current < buffer.length) {
            return buffer[current];
        }
        return -1;
    }

    public boolean readExpected(char c) {
        if (current < buffer.length) {
            if (buffer[current] == c) {
                current += 1;
                return true;
            }
        }
        return false;
    }

    public boolean readExpected(char[] expected) {
        int position = this.current;
        for (char c : expected) {
            if (!readExpected(c)) {
                this.current = position;
                return false;
            }
        }
        return true;
    }

    public void skipWhiteSpace() {
        boolean found;
        do {
            found = readExpected(Constants.SPACE);
        } while (found);
    }

    public boolean readUntil(char end) {
        while (current < buffer.length) {
            if (buffer[current] == end) {
                return true;
            }
            current += 1;
        }
        return false;
    }

    public boolean lookForAny(char... anyChar) {
        for (char c : anyChar) {
            if (eol()) {
                if (c == (char) Constants.EOL) {
                    return true;
                }
            } else if (buffer[current] == c) {
                return true;
            }
        }
        return false;
    }

    public boolean readExpectedWithDelims(char[] expected, char... delims) {
        int position = this.current;
        boolean success = readExpected(expected);
        if (success) {
            success = lookForAny(delims);
            if (success) {
                return true;
            }
        }
        this.current = position;
        return false;
    }

    @Override
    public String toString() {
        return "\"" + new String(buffer, 0, buffer.length) + "\" at position: " + current;
    }
}
