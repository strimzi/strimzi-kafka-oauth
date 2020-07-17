/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.CurrentTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class MockTimeProviderBasedScheduledExecutorService implements ScheduledExecutorService {

    private final List<MockScheduledExecutorLog> invocationLog = Collections.synchronizedList(new LinkedList<>());

    private final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final SortedMap<Long, Runnable> schedules = Collections.synchronizedSortedMap(new TreeMap<>());

    MockTimeProviderBasedScheduledExecutorService() {
        // This thread iterates the schedule, and triggers immediate execution of any scheduled job that's in the past of current time
        Thread triggeringThread = new Thread(() -> {
            while (true) {
                synchronized (schedules) {
                    Iterator<Map.Entry<Long, Runnable>> it = schedules.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Long, Runnable> next = it.next();
                        if (next.getKey() < CurrentTime.currentTime()) {
                            executor.submit(next.getValue());
                            it.remove();
                        }
                    }
                }

                // a little pause before doing the next iteration
                // it's a simple implementation :)

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }
        }, "MockTimeProviderBasedScheduledExecutorServices Job Triggering Thread");
        triggeringThread.setDaemon(true);
        triggeringThread.start();
    }

    void clearLog() {
        invocationLog.clear();
    }

    public ArrayList<MockScheduledExecutorLog> log() {
        return new ArrayList<>(invocationLog);
    }

    public Map<Long, Runnable> schedules() {
        return new LinkedHashMap(schedules);
    }

    @Override
    public void execute(Runnable command) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.EXECUTE, command));
        executor.execute(command);
    }

    @Override
    public void shutdown() {
        // noop
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
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SUBMIT, task, result));
        return executor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SUBMIT, task));
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ALL, tasks));
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ALL, tasks, timeout, unit));
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ANY, tasks));
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.INVOKE_ANY, tasks, timeout, unit));
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE, command, delay, unit));
        CompletableFuture<?> f = new CompletableFuture<>();
        // calculate point in time
        long at = CurrentTime.currentTime() + unit.toMillis(delay);
        schedules.put(at, () -> {
            try {
                command.run();
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return new MockScheduledFuture<>(f, at);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE, callable, delay, unit));
        CompletableFuture<V> f = new CompletableFuture<>();
        long at = CurrentTime.currentTime() + unit.toMillis(delay);
        schedules.put(at, () -> {
            try {
                f.complete(callable.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return new MockScheduledFuture<>(f, at);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, command, initialDelay, period, unit));
        CompletableFuture<?> f = new CompletableFuture<>();
        long at = CurrentTime.currentTime() + unit.toMillis(initialDelay);
        schedules.put(at, () -> {
            try {
                long startJob = CurrentTime.currentTime();
                command.run();
                long delay = startJob + period - CurrentTime.currentTime();
                f.complete(null);
                scheduleAtFixedRate(command, delay, period, unit);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return new MockScheduledFuture<>(f, at);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.SCHEDULE_WITH_FIXED_DELAY, command, initialDelay, delay, unit));
        CompletableFuture<?> f = new CompletableFuture<>();
        long at = CurrentTime.currentTime() + unit.toMillis(initialDelay);
        schedules.put(at, () -> {
            try {
                command.run();
                f.complete(null);
                scheduleWithFixedDelay(command, delay, delay, unit);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return new MockScheduledFuture<>(f, at);
    }
}
