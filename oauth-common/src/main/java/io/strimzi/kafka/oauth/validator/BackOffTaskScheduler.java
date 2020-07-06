/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This scheduler adds
 */
public class BackOffTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackOffTaskScheduler.class);

    private final ScheduledExecutorService service;
    private final Runnable task;

    private int minPauseSeconds = 1;
    private int maxIntervalSeconds;
    private int cutoffIntervalSeconds;

    // Only one task is scheduled at a time
    private AtomicBoolean taskScheduled = new AtomicBoolean(false);

    private long lastExecutionAttempt;

    public BackOffTaskScheduler(ScheduledExecutorService service, Runnable task) {
        this.service = service;
        this.task = task;
    }

    public int getMinPauseSeconds() {
        return minPauseSeconds;
    }

    public void setMinPauseSeconds(int minPauseSeconds) {
        this.minPauseSeconds = minPauseSeconds;
    }

    public int getMaxIntervalSeconds() {
        return maxIntervalSeconds;
    }

    public void setMaxIntervalSeconds(int maxIntervalSeconds) {
        this.maxIntervalSeconds = maxIntervalSeconds;
    }

    public int getCutoffIntervalSeconds() {
        return cutoffIntervalSeconds;
    }

    public void setCutoffIntervalSeconds(int cutoffIntervalSeconds) {
        this.cutoffIntervalSeconds = cutoffIntervalSeconds;
    }

    /**
     * Schedule a task. The task will only be scheduled if no other task is yet scheduled.
     *
     * That is to prevent piling up of tasks.
     *
     * @return true if task was scheduled for execution, false otherwise
     */
    public boolean scheduleTask() {
        // Only one scheduled task can be outstanding at any time
        if (!taskScheduled.getAndSet(true)) {
            log.debug("Acquired taskSchedule lock");

            // First repetition is immediate but at least minPauseSeconds has to pass since the last attempt
            long delay = 1;
            long now = System.currentTimeMillis();
            long boundaryTime = minPauseSeconds > 0 ? lastExecutionAttempt + minPauseSeconds * 1000L : now;
            if (boundaryTime > now) {
                delay = boundaryTime - now;
            }

            scheduleServiceTask(new RunnableTask(), delay);
            if (log.isDebugEnabled()) {
                log.debug("Task scheduled for execution in {} milliseconds", delay);
            }
            return true;
        }
        return false;
    }

    /**
     * Update the time of last execution attempt to current time.
     * This is handy when multiple schedulers work the same task on a single thread.
     */
    public void updateLastExecutionTime() {
        lastExecutionAttempt = System.currentTimeMillis();
    }

    private void scheduleServiceTask(Runnable task, long delay) {
        try {
            service.schedule(task, delay, TimeUnit.SECONDS);
        } catch (Throwable e) {
            // Release taskSchedule lock
            releaseTaskScheduleLock();

            throw new RuntimeException("Failed to re-schedule the task", e);
        }
    }

    private void releaseTaskScheduleLock() {
        taskScheduled.set(false);
        log.debug("Released taskSchedule lock");
    }

    class RunnableTask implements Runnable {

        private int repeatCount = 0;

        @Override
        public void run() {
            try {
                lastExecutionAttempt = System.currentTimeMillis();
                repeatCount += 1;

                // Delegate to task's run()
                task.run();

                // Release taskSchedule lock
                releaseTaskScheduleLock();

            } catch (Throwable t) {
                log.error("Scheduled task execution failed:", t);

                // If things went wrong, reschedule next repetition
                // in exponential backoff fashion (1,2,4,8,16,32)
                long delay = (long) Math.pow(2, repeatCount);
                if (minPauseSeconds > 0 && delay < minPauseSeconds) {
                    delay = minPauseSeconds;
                }

                // Limit the delay to maxIntervalSeconds
                // That makes it possible to grow delay exponentially to some maximum, and then keep it constant
                if (maxIntervalSeconds > 0 && delay > maxIntervalSeconds) {
                    delay = maxIntervalSeconds;
                }

                // Only reschedule if the next run would happen within cutoffIntervalSeconds
                // If there is another periodic job then we may want to stop re-scheduling this one
                // once the other job's period is reached.
                if (cutoffIntervalSeconds <= 0 || delay < cutoffIntervalSeconds) {

                    // We still hold the taskScheduled lock
                    scheduleServiceTask(this, delay);

                    if (log.isDebugEnabled()) {
                        log.debug("Task rescheduled in " + delay + " seconds");
                    }
                } else {
                    // Release taskSchedule lock
                    releaseTaskScheduleLock();
                }
            }
        }
    }
}
