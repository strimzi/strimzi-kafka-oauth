/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.CurrentTime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

class MockScheduledExecutorLog {

    MockExecutorLogActionType type;
    List<Callable<?>> tasks;
    Runnable runnable;
    Callable<?> callable;
    long initialDelay;
    long delayOrPeriod;
    TimeUnit delayUnit;
    long timeout;
    TimeUnit timeoutUnit;
    Object result;

    long entryTime = CurrentTime.currentTime();

    MockScheduledExecutorLog(MockExecutorLogActionType type, Runnable runnable) {
        this.type = type;
        this.runnable = runnable;
    }

    MockScheduledExecutorLog(MockExecutorLogActionType type, Callable<?> callable) {
        this.type = type;
        this.callable = callable;
    }

    MockScheduledExecutorLog(MockExecutorLogActionType type, Runnable runnable, Object result) {
        this.type = type;
        this.runnable = runnable;
        this.result = result;
    }

    public <T> MockScheduledExecutorLog(MockExecutorLogActionType type, Collection<? extends Callable<T>> tasks) {
        this.type = type;
        this.tasks = new ArrayList<>(tasks);
    }

    public <T> MockScheduledExecutorLog(MockExecutorLogActionType type, Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        this.type = type;
        this.tasks = new ArrayList<>(tasks);
        this.timeout = timeout;
        this.timeoutUnit = unit;
    }

    public MockScheduledExecutorLog(MockExecutorLogActionType type, Runnable task, long delay, TimeUnit unit) {
        this.type = type;
        this.runnable = task;
        this.delayOrPeriod = delay;
        this.delayUnit = unit;
    }

    public MockScheduledExecutorLog(MockExecutorLogActionType type, Callable<?> callable, long delay, TimeUnit unit) {
        this.type = type;
        this.callable = callable;
        this.delayOrPeriod = delay;
        this.delayUnit = unit;
    }

    public MockScheduledExecutorLog(MockExecutorLogActionType type, Runnable command, long initialDelay, long delayOrPeriod, TimeUnit unit) {
        this.type = type;
        this.runnable = command;
        this.initialDelay = initialDelay;
        this.delayOrPeriod = delayOrPeriod;
        this.delayUnit = unit;
    }

    @Override
    public String toString() {
        switch (type) {
            case EXECUTE:
                return formatDateTime() + " " + type;
            case SCHEDULE:
                return formatDateTime() + " " + type + " delayOrPeriod: " + delayOrPeriod + ", unit: " + delayUnit;
            default:
                return formatDateTime() + " " + type + " delayOrPeriod: " + delayOrPeriod + ", unit: " + delayUnit +
                        ", initialDelay: " + initialDelay + ", timeout: " + timeout + ", timeoutUnit: " + timeoutUnit;
        }
    }

    private String formatDateTime() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(entryTime).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
}
