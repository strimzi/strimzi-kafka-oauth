/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.CurrentTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This scheduler adds support to immediately re-schedule the execution of the provided task, using the provided <em>ExecutorService</em>.
 * <p>
 * It is used by <em>JWTSignatureValidator</em> to perform immediate (out of regular schedule) keys refresh upon detection
 * of unknown keys. The objective is to detect rotation of JWT signing keys on authorization server very quickly in order to
 * minimize the mismatch between valid / invalid keys on the Kafka broker and on the authorization server. Until the keys
 * are in-sync with the authorization server, the mismatch causes 'invalid token' errors for valid new tokens, and keeps
 * the Kafka broker accepting the no-longer-valid tokens.
 * </p>
 * <p>
 * This scheduler works in tandem with periodic keys refresh job in <em>JWTSignatureValidator</em>, using the same
 * <em>ScheduledExecutorService</em>, running the tasks on the same thread.
 * While the periodic refresh is triggered on a regular schedule, and is unaware of any other tasks, this class runs
 * the refresh task on demand as a one-off, but tries hard to succeed - it reschedules the task if it fails.
 * </p>
 * <p>
 * If the task has already been scheduled (by calling {@link #scheduleTask()} and has not yet successfully completed,
 * another request to schedule the task will be ignored.
 * </p>
 * <p>
 * If the scheduled task fails during its run, it will be rescheduled using the so called 'exponential backoff' delay.
 * Rather than being attempted again immediately, it will pause for an ever increasing time delay until some cutoff delay
 * is reached ({@link #cutoffIntervalSeconds}) when no further attempts are schedule, and another {@link #scheduleTask()}
 * call can again trigger a new refresh.
 * </p>
 */
public class BackOffTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackOffTaskScheduler.class);

    private final ScheduledExecutorService service;
    private final Runnable task;

    private final int minPauseSeconds;
    private final int cutoffIntervalSeconds;

    // Only one task is scheduled at a time
    private final AtomicBoolean taskScheduled = new AtomicBoolean(false);

    private long lastExecutionAttempt;

    /**
     * Initialise a new scheduler instance
     * @param executor Single threaded executor service, also used by periodic keys refresh job
     * @param minPauseSeconds A minimum pause before starting the task, and between any subsequent attempt
     * @param cutoffIntervalSeconds If exponential backoff pause exceeds this interval, the task is cancelled
     * @param task The task that refreshes the keys
     */
    public BackOffTaskScheduler(ScheduledExecutorService executor, int minPauseSeconds, int cutoffIntervalSeconds, Runnable task) {
        this.service = executor;
        this.task = task;
        this.minPauseSeconds = minPauseSeconds;
        this.cutoffIntervalSeconds = cutoffIntervalSeconds;

        if (minPauseSeconds < 0) {
            throw new IllegalArgumentException("'minPauseSeconds' can't be < 0");
        }

        if (cutoffIntervalSeconds < 0) {
            throw new IllegalArgumentException("'cutoffIntervalSeconds' can't be < 0");
        }
    }

    public int getMinPauseSeconds() {
        return minPauseSeconds;
    }

    public int getCutoffIntervalSeconds() {
        return cutoffIntervalSeconds;
    }

    /**
     * Schedule a task. The task will only be scheduled if no other task is yet scheduled.
     *
     * That is to prevent queueing up of tasks and unnecessary repetition of the task execution.
     *
     * @return true if the task was scheduled for execution, false otherwise
     */
    public boolean scheduleTask() {
        // Only one scheduled task can be outstanding at any time
        if (!taskScheduled.getAndSet(true)) {
            log.debug("Acquired taskSchedule lock");

            // First repetition is immediate but at least minPauseSeconds has to pass since the last attempt
            long delay = 0;
            long now = CurrentTime.currentTime();
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

    private void scheduleServiceTask(Runnable task, long delay) {
        try {
            service.schedule(task, delay, TimeUnit.MILLISECONDS);
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
                lastExecutionAttempt = CurrentTime.currentTime();
                repeatCount += 1;

                // Delegate to task's run()
                task.run();

                // Release taskSchedule lock
                releaseTaskScheduleLock();

            } catch (Throwable t) {
                log.error("Scheduled task execution failed:", t);

                // makes no sense to wait for hours until next refresh
                // this is some kind of overflow protection at ~4.5 hours.
                if (repeatCount > 14) {
                    log.debug("Task schedule lock held for too many repetitions");
                    releaseTaskScheduleLock();
                    return;
                }

                // If things went wrong, reschedule next repetition
                // in exponential backoff fashion (1,2,4,8,16,32)
                long delay = 1L << repeatCount;
                if (minPauseSeconds > 0 && delay < minPauseSeconds) {
                    delay = minPauseSeconds;
                }

                // Only reschedule if the next run would happen within cutoffIntervalSeconds
                // If there is another periodic job then we may want to stop re-scheduling this one
                // once the other job's period is reached.
                if (cutoffIntervalSeconds <= 0 || delay < cutoffIntervalSeconds) {

                    // We still hold the taskScheduled lock
                    scheduleServiceTask(this, 1000 * delay);

                    if (log.isDebugEnabled()) {
                        log.debug("Task rescheduled in {} seconds", delay);
                    }
                } else {
                    // Release taskSchedule lock
                    releaseTaskScheduleLock();
                }
            }
        }
    }
}
