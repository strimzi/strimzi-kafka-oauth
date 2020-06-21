/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class BackOffTaskSchedulerTest {

    @Test
    public void testSuccessfulTask() {

        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            counter.incrementAndGet();
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, task);
        scheduler.setMinPauseSeconds(5);

        scheduler.scheduleTask();

        // Test that exactly one task is scheduled, and scheduled immediately
        System.out.println("Executor log: ");
        for (MockScheduledExecutorLog e: executor.invocationLog) {
            System.out.println(e);
        }

        Assert.assertEquals("Executor log should have 1 entry", 1, executor.invocationLog.size());

        MockScheduledExecutorLog entry = executor.invocationLog.getFirst();
        assertLogEntry(entry, MockExecutorLogActionType.SCHEDULE, 1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testFailingTask() {

        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            int count = counter.incrementAndGet();

            // Fail five times, then succeed
            if (count <= 5) {
                throw new RuntimeException("Test failure (deliberate)");
            }
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, task);
        scheduler.setMinPauseSeconds(5);

        scheduler.scheduleTask();

        // wait for the max of 5 seconds until the task was scheduled an expected number of times
        int expected = 6;
        for (int timeout = 10; counter.get() < expected && timeout > 0; timeout--) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }

        System.out.println("Executor log: ");
        for (MockScheduledExecutorLog e: executor.invocationLog) {
            System.out.println(e);
        }

        // Test that the failing task is re-scheduled with exponential backoff
        Assert.assertEquals("Executor log should have 6 entries", 6, executor.invocationLog.size());

        Iterator<MockScheduledExecutorLog> it = executor.invocationLog.iterator();

        Assert.assertTrue("Has more entries", it.hasNext());
        assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 1, TimeUnit.MILLISECONDS);

        Iterator<Integer> delayIt = Arrays.asList(5, 5, 8, 16, 32).iterator();
        while (delayIt.hasNext()) {
            Assert.assertTrue("Has more entries", it.hasNext());
            assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, delayIt.next(), TimeUnit.SECONDS);
        }

        Assert.assertFalse("Has no more entries", it.hasNext());
    }

    @Test
    public void testFailingTaskWithCutoff() {

        MockScheduledExecutorService executor = new MockScheduledExecutorService();
        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            counter.incrementAndGet();
            // Always fail
            throw new RuntimeException("Test failure (deliberate)");
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, task);
        scheduler.setMinPauseSeconds(5);
        scheduler.setCutoffIntervalSeconds(240);

        scheduler.scheduleTask();

        // wait for the max of 5 seconds until the task was scheduled an expected number of times
        int expected = 8;
        for (int timeout = 10; counter.get() < expected && timeout > 0; timeout--) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }

        System.out.println("Executor log: ");
        for (MockScheduledExecutorLog e: executor.invocationLog) {
            System.out.println(e);
        }

        // Test that the failing task is re-scheduled with exponential backoff until the delay reaches 240 seconds
        Assert.assertEquals("Executor log should have 8 entries", 8, executor.invocationLog.size());

        Iterator<MockScheduledExecutorLog> it = executor.invocationLog.iterator();

        Assert.assertTrue("Has more entries", it.hasNext());
        assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 1, TimeUnit.MILLISECONDS);

        Iterator<Integer> delayIt = Arrays.asList(5, 5, 8, 16, 32, 64, 128).iterator();
        while (delayIt.hasNext()) {
            Assert.assertTrue("Has more entries", it.hasNext());
            assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, delayIt.next(), TimeUnit.SECONDS);
        }

        Assert.assertFalse("Has no more entries", it.hasNext());
    }

    @Test
    public void testFailingTaskWithMaxInterval() {

        MockScheduledExecutorService executor = new MockScheduledExecutorService();
        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            int count = counter.incrementAndGet();
            // Fail first 10 times
            if (count < 11) {
                throw new RuntimeException("Test failure (deliberate)");
            }
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, task);
        scheduler.setMaxIntervalSeconds(240);

        scheduler.scheduleTask();

        // wait for the max of 5 seconds until the task was scheduled an expected number of times
        int expected = 11;
        for (int timeout = 10; counter.get() < expected && timeout > 0; timeout--) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }

        System.out.println("Executor log: ");
        for (MockScheduledExecutorLog e: executor.invocationLog) {
            System.out.println(e);
        }

        // Test that the failing task is re-scheduled with exponential backoff until the delay reaches 240 seconds
        Assert.assertEquals("Executor log should have 11 entries", 11, executor.invocationLog.size());

        Iterator<MockScheduledExecutorLog> it = executor.invocationLog.iterator();

        Assert.assertTrue("Has more entries", it.hasNext());
        assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 1, TimeUnit.MILLISECONDS);

        Iterator<Integer> delayIt = Arrays.asList(2, 4, 8, 16, 32, 64, 128, 240, 240, 240).iterator();
        while (delayIt.hasNext()) {
            Assert.assertTrue("Has more entries", it.hasNext());
            assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, delayIt.next(), TimeUnit.SECONDS);
        }

        Assert.assertFalse("Has no more entries", it.hasNext());
    }

    @Test
    public void testFloodRequests() {
        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            counter.incrementAndGet();

            try {
                // Simulate the task taking some time
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted");
            }
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, task);

        // Flood with schedule requests
        for (int i = 0; i < 100; i++) {
            scheduler.scheduleTask();
        }

        Assert.assertEquals("Executor log should have 1 entry", 1, executor.invocationLog.size());

        MockScheduledExecutorLog entry = executor.invocationLog.getFirst();
        assertLogEntry(entry, MockExecutorLogActionType.SCHEDULE, 1, TimeUnit.MILLISECONDS);
    }

    private static void assertLogEntry(MockScheduledExecutorLog entry, MockExecutorLogActionType type, int delayOrPeriod, TimeUnit delayUnit) {
        Assert.assertEquals("Entry is of type " + type, type, entry.type);
        Assert.assertEquals("DelayOrPeriod is " + delayOrPeriod, delayOrPeriod, entry.delayOrPeriod);
        Assert.assertEquals("DelayUnit is " + delayUnit, delayUnit, entry.delayUnit);
    }


    static class MockScheduledExecutorService implements ScheduledExecutorService {

        LinkedList<MockScheduledExecutorLog> invocationLog = new LinkedList<>();


        void clearLog() {
            invocationLog.clear();
        }

        @Override
        public void execute(Runnable command) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.EXECUTE, command));
            command.run();
        }

        @Override
        public void shutdown() {
            System.out.println("shutdown()");
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SUBMIT, task));
            CompletableFuture<T> f = new CompletableFuture<>();
            try {
                f.complete(task.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
            return f;
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SUBMIT, task, result));
            CompletableFuture<T> f = new CompletableFuture<>();
            try {
                task.run();
                f.complete(result);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
            return f;
        }

        @Override
        public Future<?> submit(Runnable task) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SUBMIT, task));
            return submit(task, null);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ALL, tasks));
            ArrayList<Future<T>> result = new ArrayList<>(tasks.size());
            for (Callable<T> task: tasks) {
                result.add(submit(task));
            }
            for (Future<T> f: result) {
                try {
                    f.get();
                } catch (Exception ignored) { }
            }
            return result;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ALL, tasks, timeout, unit));
            ArrayList<Future<T>> result = new ArrayList<>(tasks.size());
            for (Callable<T> task: tasks) {
                result.add(submit(task));
            }

            long remaining = unit.toMillis(timeout);
            long maxTime = System.currentTimeMillis() + remaining;

            boolean cancel = false;

            for (Future<T> f: result) {
                if (cancel) {
                    f.cancel(false);
                    continue;
                }
                try {
                    f.get(maxTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    cancel = true;
                }
            }
            return result;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ANY, tasks));
            ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
            for (Callable<T> task: tasks) {
                futures.add(submit(task));
            }

            T result = null;
            boolean cancel = false;

            for (Future<T> f: futures) {
                if (cancel) {
                    f.cancel(false);
                    continue;
                }
                try {
                    result = f.get();
                    cancel = true;
                } catch (Exception ignored) {
                }
            }
            return result;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ANY, tasks, timeout, unit));
            ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
            for (Callable<T> task: tasks) {
                futures.add(submit(task));
            }

            long remaining = unit.toMillis(timeout);
            long maxTime = System.currentTimeMillis() + remaining;

            T result = null;
            boolean cancel = false;

            for (Future<T> f: futures) {
                if (cancel) {
                    f.cancel(false);
                    continue;
                }
                try {
                    result = f.get(maxTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    cancel = true;
                } catch (Exception ignored) {
                }
            }
            return result;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE, command, delay, unit));
            CompletableFuture<?> f = new CompletableFuture<>();
            executeTask(f, command);
            return new MockScheduledFuture<>(f, System.currentTimeMillis() + unit.toMillis(delay));
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE, callable, delay, unit));
            CompletableFuture<V> f = new CompletableFuture<>();
            executeTask(f, callable);
            return new MockScheduledFuture<>(f, System.currentTimeMillis() + unit.toMillis(delay));
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, command, initialDelay, period, unit));
            CompletableFuture<?> f = new CompletableFuture<>();
            executeTask(f, command);
            return new MockScheduledFuture<>(f, System.currentTimeMillis() + unit.toMillis(initialDelay));
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE_WITH_FIXED_DELAY, command, initialDelay, delay, unit));
            CompletableFuture<?> f = new CompletableFuture<>();
            executeTask(f, command);
            return new MockScheduledFuture<>(f, System.currentTimeMillis() + unit.toMillis(initialDelay));
        }
    }

    static void executeTask(CompletableFuture<?> f, Runnable command) {
        Thread thread = new Thread(() -> {
            try {
                command.run();
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        thread.start();
    }

    static <V> void executeTask(CompletableFuture<V> f, Callable<V> callable) {
        Thread thread = new Thread(() -> {
            try {
                f.complete(callable.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        thread.start();
    }

    static class MockScheduledExecutorLog {

        MockExecutorLogActionType type;
        List<Callable<?>> tasks;
        Runnable runnable;
        Callable callable;
        long initialDelay;
        long delayOrPeriod;
        TimeUnit delayUnit;
        long timeout;
        TimeUnit timeoutUnit;
        Object result;

        long entryTime = System.currentTimeMillis();

        MockScheduledExecutorLog(MockExecutorLogActionType type, Runnable runnable) {
            this.type = type;
            this.runnable = runnable;
        }

        MockScheduledExecutorLog(MockExecutorLogActionType type, Callable callable) {
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

        public MockScheduledExecutorLog(MockExecutorLogActionType type, Callable callable, long delay, TimeUnit unit) {
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

    enum MockExecutorLogActionType {
        SCHEDULE_WITH_FIXED_DELAY,
        SCHEDULE_AT_FIXED_RATE,
        SCHEDULE,
        INVOKE_ANY,
        INVOKE_ALL,
        SUBMIT,
        EXECUTE
    }

    static class MockScheduledFuture<T> implements ScheduledFuture<T> {

        private long scheduledAt;

        private CompletableFuture<T> future;

        MockScheduledFuture(CompletableFuture<T> future, long scheduledAt) {
            this.future = future;
            this.scheduledAt = scheduledAt;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = scheduledAt - System.currentTimeMillis();
            switch (unit) {
                case MILLISECONDS:
                    return remaining;
                case SECONDS:
                    return TimeUnit.MILLISECONDS.toSeconds(remaining);
                case MINUTES:
                    return TimeUnit.MILLISECONDS.toMinutes(remaining);
                case HOURS:
                    return TimeUnit.MILLISECONDS.toHours(remaining);
                case DAYS:
                    return TimeUnit.MILLISECONDS.toDays(remaining);
                default:
                    throw new IllegalArgumentException("Unsupported TimeUnit: " + unit);
            }
        }

        @Override
        public int compareTo(Delayed o) {
            long diff = scheduledAt - o.getDelay(TimeUnit.MILLISECONDS);
            return diff < 0 ? -1 : diff > 0 ? 1 : 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }
    }
}
