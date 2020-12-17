/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.jsonpath;

/**
 * This class contains low level reading methods with positional tracking to facilitate higher order parsing operations.
 *
 * The current position always points to the character that will be read next, when one of the reading methods
 * is called.
 * The read*() methods increment the current position, the peek*() methods do not.
 * The unread() method decrements the current position.
 */
class ParsingContext {

    char[] buffer;
    int start;
    int current;

    /**
     * Create a new ParsingContext instance using the specified buffer, and initialising the start and current position to 0.
     *
     * @param buffer Parsing buffer
     */
    ParsingContext(char[] buffer) {
        this(buffer, 0, 0);
    }

    /**
     * Create a new ParsingContext instance using the specified buffer, and initialising the start and
     * current positions to the specified values.
     *
     * @param buffer Parsing buffer
     * @param start Start position
     * @param current Current read position
     */
    ParsingContext(char[] buffer, int start, int current) {
        this.buffer = buffer;
        this.start = start;
        this.current = current;
    }

    /**
     * Reset the current position to the start position.
     */
    public ParsingContext reset() {
        current = start;
        return this;
    }

    /**
     * Reset the current position to the specified offset.
     *
     * @param offset The new position
     */
    public void resetTo(int offset) {
        if (offset < start || offset > buffer.length) {
            throw new IllegalArgumentException("Invalid offset");
        }
        current = offset;
    }

    /**
     * Reset the start position to the current position.
     *
     * @return The new start position
     */
    public int resetStart() {
        start = current;
        return start;
    }

    /**
     * Decrement the current position by one.
     *
     * @return The new current position
     */
    public int unread() {
        if (current <= buffer.length && current > 0) {
            current -= 1;
        }
        return current;
    }

    /**
     * Get the number of characters read since last resetting the start position.
     *
     * @return The number of characters
     */
    public int count() {
        return current - start;
    }

    /**
     * Has current reading position reached the end of the buffer.
     *
     * @return true if current position has reached the end of the buffer
     */
    public boolean eol() {
        return current >= buffer.length;
    }

    /**
     * Read a single character, incrementing the current position by one.
     *
     * @return The character read or -1 if end of the buffer was reached
     */
    public int read() {
        if (current < buffer.length) {
            return buffer[current++];
        }
        return -1;
    }

    /**
     * Read a single character at current position without incrementing the current position.
     *
     * @return The character read or -1 if end of the buffer was reached
     */
    public int peek() {
        if (current < buffer.length) {
            return buffer[current];
        }
        return -1;
    }

    /**
     * Increment the current position if the current character is as expected, otherwise do nothing.
     *
     * @param c The expected character
     * @return true if the current position was incremented
     */
    public boolean readExpected(char c) {
        if (current < buffer.length) {
            if (buffer[current] == c) {
                current += 1;
                return true;
            }
        }
        return false;
    }

    /**
     * Increment the current position if the current character is one of the expected characters, otherwise do nothing
     *
     * @param expected The expected characters
     * @return true if the current position was incremented
     */
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

    /**
     * Increment the current position until a non-space character or end-of-buffer is reached.
     *
     * Only ASCII 32 (' ') counts as a space character.
     */
    public void skipWhiteSpace() {
        boolean found;
        do {
            found = readExpected(Constants.SPACE);
        } while (found);
    }

    /**
     * Increment the current position until the specified character is found or end-of-buffer is reached.
     *
     * @param end The character to look for
     * @return true if the specified character was found, false otherwise
     */
    public boolean readUntil(char end) {
        while (current < buffer.length) {
            if (buffer[current] == end) {
                return true;
            }
            current += 1;
        }
        return false;
    }

    /**
     * Check if the current position contains any of the specified characters.
     *
     * The specified characters can include Constant.EOL, which checks if the buffer has already been completely read.
     *
     * @param anyChar The characters to check for
     * @return true if the character at the current position is any of the specified characters
     */
    public boolean peekForAny(char... anyChar) {
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

    /**
     * Read the buffer characters starting from current position and match the expected array of characters,
     * and are followed by one of the specified delimiter characters.
     *
     * If match is found, increment the current position for the expected buffer length, otherwise leave the current position unchanged.
     *
     * @param expected The array of characters to match against
     * @param delims The allowed delimiters following the expected array
     * @return true if the match was found, and current position was incremented
     */
    public boolean readExpectedWithDelims(char[] expected, char... delims) {
        int position = this.current;
        boolean success = readExpected(expected);
        if (success) {
            success = peekForAny(delims);
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
