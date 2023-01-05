/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicCoin {

    private AtomicLong counter;

    public AtomicCoin() {
        counter = new AtomicLong();
    }

    public AtomicCoin(boolean faceUp) {
        counter = new AtomicLong(faceUp ? 1 : 0);
    }

    public boolean flip() {
        return counter.incrementAndGet() % 2 == 1;
    }

    public boolean isFaceUp() {
        return counter.get() % 2 == 1;
    }
}
