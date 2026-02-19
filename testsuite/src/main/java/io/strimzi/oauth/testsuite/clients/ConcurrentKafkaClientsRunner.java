/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.oauth.testsuite.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Utility for running multiple test tasks concurrently with a start barrier and configurable timeout.
 * Replaces legacy Thread-extending FloodProducer/FloodConsumer patterns with modern concurrency.
 */
public class ConcurrentKafkaClientsRunner {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentKafkaClientsRunner.class);

    private final List<Callable<Void>> tasks = new ArrayList<>();

    /**
     * Add a task to be executed concurrently.
     *
     * @param task the task to run
     * @return this runner for chaining
     */
    public ConcurrentKafkaClientsRunner addTask(Callable<Void> task) {
        tasks.add(task);
        return this;
    }

    /**
     * Execute all added tasks concurrently. A CountDownLatch ensures all tasks start simultaneously.
     * Each task's result is retrieved with the given timeout.
     *
     * @param timeoutSeconds maximum time to wait for all tasks to complete
     * @throws Exception if any task fails
     */
    public void executeAll(int timeoutSeconds) throws Exception {
        if (tasks.isEmpty()) {
            return;
        }

        int taskCount = tasks.size();
        CountDownLatch startLatch = new CountDownLatch(taskCount);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(() -> {
                    startLatch.countDown();
                    startLatch.await();
                    return task.call();
                }));
            }

            for (Future<Void> future : futures) {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Clear all added tasks to prepare for the next run.
     */
    public void clear() {
        tasks.clear();
    }
}
