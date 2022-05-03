/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class MockScheduledExecutorService implements ScheduledExecutorService {

    private final List<MockScheduledExecutorLog> invocationLog = Collections.synchronizedList(new LinkedList<>());

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


    void clearLog() {
        invocationLog.clear();
    }

    public LinkedList<MockScheduledExecutorLog> invocationLog() {
        return new LinkedList<>(invocationLog);
    }

    @Override
    public void execute(Runnable command) {
        invocationLog.add(new MockScheduledExecutorLog(MockExecutorLogActionType.EXECUTE, command));
        command.run();
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
    public boolean awaitTermination(long timeout, TimeUnit unit) {
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
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
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
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
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
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
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
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
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
