/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Use daemon thread for refresh job
 */
public class DaemonThreadFactory implements ThreadFactory {

    public Thread newThread(Runnable r) {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    }
}
