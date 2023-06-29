/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import io.strimzi.kafka.oauth.services.CurrentTime;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for BackOffTaskScheduler
 */
public class BackOffTaskSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(BackOffTaskSchedulerTest.class);

    @Test
    public void testSuccessfulTask() {

        printTitle("BackOffTaskSchedulerTest :: testSuccessfulTask");

        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = counter::incrementAndGet;

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, 5, 300, task);
        scheduler.scheduleTask();

        dumpExecutorLog(executor);

        // Test that exactly one task is scheduled, and scheduled immediately
        Assert.assertEquals("Executor log should have 1 entry", 1, executor.invocationLog().size());

        MockScheduledExecutorLog entry = executor.invocationLog().getFirst();
        assertLogEntry(entry, MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testFailingTaskEventuallySucceeds() {

        printTitle("BackOffTaskSchedulerTest :: testFailingTaskEventuallySucceeds");

        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            int count = counter.incrementAndGet();

            // Fail five times, then succeed
            if (count <= 5) {
                throw new RuntimeException("Test failure (deliberate)");
            }
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, 5, 300, task);
        scheduler.scheduleTask();

        // Wait for the max of 5 seconds until the task was scheduled an expected number of times
        int expected = 6;
        waitForTargetCount(counter, expected);

        dumpExecutorLog(executor);

        // Test that the failing task is re-scheduled with exponential backoff
        Assert.assertEquals("Executor log should have 6 entries", 6, executor.invocationLog().size());

        Iterator<MockScheduledExecutorLog> it = executor.invocationLog().iterator();

        Assert.assertTrue("Has more entries", it.hasNext());
        assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);

        for (int i : Arrays.asList(5, 5, 8, 16, 32)) {
            Assert.assertTrue("Has more entries", it.hasNext());
            assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 1000 * i, TimeUnit.MILLISECONDS);
        }

        Assert.assertFalse("Has no more entries", it.hasNext());
    }

    @Test
    public void testFailingTaskWithCutoff() {

        printTitle("BackOffTaskSchedulerTest :: testFailingTaskWithCutoff");

        MockScheduledExecutorService executor = new MockScheduledExecutorService();
        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            counter.incrementAndGet();
            // Always fail
            throw new RuntimeException("Test failure (deliberate)");
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, 5, 250, task);
        scheduler.scheduleTask();

        // wait for the max of 5 seconds until the task was scheduled an expected number of times
        int expected = 8;
        waitForTargetCount(counter, expected);

        dumpExecutorLog(executor);

        // Test that the failing task is re-scheduled with exponential backoff until the delay reaches 240 seconds
        Assert.assertEquals("Executor log should have 8 entries", 8, executor.invocationLog().size());

        Iterator<MockScheduledExecutorLog> it = executor.invocationLog().iterator();

        Assert.assertTrue("Has more entries", it.hasNext());
        assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);

        for (int i : Arrays.asList(5, 5, 8, 16, 32, 64, 128)) {
            Assert.assertTrue("Has more entries", it.hasNext());
            assertLogEntry(it.next(), MockExecutorLogActionType.SCHEDULE, 1000 * i, TimeUnit.MILLISECONDS);
        }

        Assert.assertFalse("Has no more entries", it.hasNext());
    }

    @Test
    public void testRegularAndFastSchedulerInteractionWithMockTime() {

        printTitle("BackOffTaskSchedulerTest :: testRegularAndFastSchedulerInteractionWithMockTime");

        // Set mock current time provider, that will be picked by BackOffTaskScheduler
        MockCurrentTimeProvider timeProvider = new MockCurrentTimeProvider();
        timeProvider.setTime(0);
        CurrentTime.setCurrentTimeProvider(timeProvider);

        // Instantiate the mock scheduled executor which executes tasks using mock current time rather than the real clock
        MockTimeProviderBasedScheduledExecutorService executor = new MockTimeProviderBasedScheduledExecutorService();
        AtomicInteger theTaskCounter = new AtomicInteger();
        AtomicInteger regularTriggerCounter = new AtomicInteger();

        // Number of seconds the task 'takes' to execute (simulated time)
        // WARNING: You can't just change this value
        final int taskDuration = 4;

        // The regular refresh schedule - every 60 seconds (scheduled execution period)
        // WARNING: You can't just change this value
        final int regularSeconds = 60;

        // minimum pause time between consecutive job runs
        // WARNING: You can't just change this value
        final int minPauseSeconds = 5;

        // The Task - always executed through BackOffTaskScheduler
        Runnable theTask = () -> {
            log.info("Task start time: {}", timeProvider.currentTime());

            // Simulate the task taking a while to execute
            timeProvider.addSeconds(taskDuration);

            log.info("Task end time: {}", timeProvider.currentTime());

            int count = theTaskCounter.incrementAndGet();
            log.info("Count == {}", count);

            // Fail by default
            throw new RuntimeException("Test failure (deliberate)");
        };

        // we'll store here the results of calling BackOffTaskScheduler.scheduleTask()
        // True if no other schedule is holding the lock, and The Task was successfully scheduled
        List<Boolean> scheduleSuccess = Collections.synchronizedList(new ArrayList<>());

        // That's the scheduler whose behavior we're testing
        // It executes the task ASAP after scheduleTask() is called on it
        // But it makes a minPauseSeconds pause first, and it cancels re-attempts if exponential backoff
        // pause between reattempts reaches regularSeconds
        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, minPauseSeconds, regularSeconds, theTask);

        // The regularSeconds job
        // The job will execute every regularSeconds of simulated time
        // It does not perform The Task - just triggers its execution
        Runnable regularTriggerTask = () -> {
            try {
                scheduleSuccess.add(scheduler.scheduleTask());
                regularTriggerCounter.incrementAndGet();
            } catch (Exception e) {
                // Log, but don't rethrow the exception to prevent scheduler cancelling the scheduled job.
                log.error("{}", e.getMessage(), e);
            }
        };

        //
        // Start testing
        //

        // Schedule The Trigger Task to go off at 60s, and then every next 60 seconds
        executor.scheduleAtFixedRate(regularTriggerTask, regularSeconds, regularSeconds, TimeUnit.SECONDS);

        // Test that a task was scheduled 60 seconds into the future
        List<MockScheduledExecutorLog> elog = executor.log();
        Assert.assertEquals("Has 1 entry", 1, elog.size());
        assertLogEntry(elog.get(0), MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, regularSeconds, TimeUnit.SECONDS);

        // Assert execution count of The Task (which is scheduled from The Trigger Task)
        Assert.assertEquals("Execution count zero", 0, theTaskCounter.get());

        testFirstExecution(timeProvider, executor, theTaskCounter, regularSeconds);
        testSecondExecution(timeProvider, executor, theTaskCounter, minPauseSeconds);
        testThirdExecution(timeProvider, executor, theTaskCounter, minPauseSeconds);
        testFourthExecution(timeProvider, executor, theTaskCounter);
        testFifthExecution(timeProvider, executor, theTaskCounter);

        //dumpScheduleSuccessLog(scheduleSuccess);
        //dumpSchedules(executor.schedules());

        testSecondTriggerTask(timeProvider, executor, theTaskCounter, regularTriggerCounter);
        testSixthExecution(timeProvider, executor, theTaskCounter);
        testThirdTriggerTask(timeProvider, executor, theTaskCounter, regularTriggerCounter);
    }

    private void testFirstExecution(MockCurrentTimeProvider timeProvider,
                                    MockTimeProviderBasedScheduledExecutorService executor,
                                    AtomicInteger theTaskCounter,
                                    int regularSeconds) {

        // Move the simulated time regularSeconds (60 sesc) into the future
        // The regular job should then get executed in very short time
        timeProvider.addSeconds(regularSeconds);

        // Wait for The Task to be executed
        int expected = 1;
        waitForTargetCount(theTaskCounter, expected);

        // Fetch executor log
        List<MockScheduledExecutorLog> elog = executor.log();

        // The second call to executor happens when The Trigger Task is triggered, which which schedules The Task for immediate execution
        // It must exist, otherwise the counter couldn't have been incremented
        Assert.assertTrue("Has at least 2 entries :: " + elog, elog.size() >= 2);
        assertLogEntry(elog.get(1), MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);

        // Possibly wait some more for the next Trigger Task to be scheduled
        // The mechanics of how repeated scheduling is implemented is for The Trigger Task to schedule the next Trigger Task
        // It does that after the Trigger Task successfully completes
        elog = waitForEntryCount(executor, 3);

        Assert.assertTrue("Has at least 3 entries :: " + elog, elog.size() >= 3);
        MockScheduledExecutorLog entry = elog.get(2);
        Assert.assertEquals("Entry is of type " + MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, entry.type);
        Assert.assertTrue("DelayOrPeriod is less or equal " + regularSeconds, entry.delayOrPeriod <= regularSeconds);
        Assert.assertEquals("DelayUnit is " + TimeUnit.SECONDS, TimeUnit.SECONDS, entry.delayUnit);
    }

    private void testSecondExecution(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter, int minPauseSeconds) {
        // Move current time for minPauseSeconds into the future
        timeProvider.addSeconds(minPauseSeconds);

        // The Task should get executed again because it's supposed to fail several times,
        // which is why it gets automatically re-scheduled
        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 4);

        Assert.assertTrue("Has at least 4 entries :: " + elog, elog.size() >= 4);
        assertLogEntry(elog.get(3), MockExecutorLogActionType.SCHEDULE, minPauseSeconds * 1000, TimeUnit.MILLISECONDS);

        // Wait for The Task to be executed the second time
        int expected = 2;
        waitForTargetCount(theTaskCounter, expected);

        // Possibly wait some more for next fast execution task to be scheduled
        elog = waitForEntryCount(executor, 5);
        assertLogEntry(elog.get(4), MockExecutorLogActionType.SCHEDULE, minPauseSeconds * 1000, TimeUnit.MILLISECONDS);
    }

    private void testThirdExecution(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter, int minPauseSeconds) {
        // Move current time minPauseSeconds into the future again
        timeProvider.addSeconds(minPauseSeconds);

        // Wait for The Task to be executed the third time
        int expected = 3;
        waitForTargetCount(theTaskCounter, expected);

        // Possibly wait some more for next fast execution task to be scheduled
        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 6);

        // Now the exponential back-off should reach time greater than 5 seconds, which means
        // it will now delay execution for 8 seconds
        Assert.assertTrue("Has at least 6 entries :: " + elog, elog.size() >= 6);
        assertLogEntry(elog.get(5), MockExecutorLogActionType.SCHEDULE, 8000, TimeUnit.MILLISECONDS);

        // With taskDuration = 4 we should after 3 executions be at around 22 seconds (plus 60 seconds initial offset)
        // immediate 4s execution + 2 x (5s pause + 4s execution)
        log.info("Current time: " + CurrentTime.currentTime());
    }

    private void testFourthExecution(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter) {
        // Move 8 secs into the future again
        // which should trigger the fourth execution of The Task
        timeProvider.addSeconds(8);

        int expected = 4;
        waitForTargetCount(theTaskCounter, expected);

        // Possibly wait some more for next fast execution task to be scheduled
        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 7);

        // it will now delay execution for 16 seconds
        Assert.assertTrue("Has at least 7 entries :: " + elog, elog.size() >= 7);
        assertLogEntry(elog.get(6), MockExecutorLogActionType.SCHEDULE, 16000, TimeUnit.MILLISECONDS);

        // With taskDuration = 4 we should after 4 executions be at around 34 seconds (plus 60 seconds initial offset)
        // immediate 4s execution + 2 x (5s pause + 4s execution) + 8s pause + 4s execution
        log.info("Current time: " + CurrentTime.currentTime());
    }

    private void testFifthExecution(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter) {
        // Move 16 secs into the future
        // which should trigger the fifth execution of The Task
        timeProvider.addSeconds(16);

        int expected = 5;
        waitForTargetCount(theTaskCounter, expected);

        // Possibly wait some more for next fast execution task to be scheduled
        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 8);

        // it will now delay next execution for 32 seconds
        Assert.assertTrue("Has at least 8 entries :: " + elog, elog.size() >= 8);
        assertLogEntry(elog.get(7), MockExecutorLogActionType.SCHEDULE, 32000, TimeUnit.MILLISECONDS);

        // With taskDuration = 4 we should after 5 executions be at around 54 seconds (plus 60 seconds initial offset)
        // immediate 4s execution + 2 x (5s pause + 4s execution) + 8s pause + 4s execution + 16s pause + 4s
        log.info("Current time (5): " + CurrentTime.currentTime());
    }

    private void testSecondTriggerTask(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter, AtomicInteger regularTriggerCounter) {
        // The Trigger Task has thus far been executed once or twice - most likely some race condition somewhere in mock classes :O
        int scheduledCount = regularTriggerCounter.get();
        Assert.assertTrue("Trigger Task has been executed once", scheduledCount >= 1 && scheduledCount <= 2);

        // Move 10 secs into the future which should trigger The Trigger Task.
        // There is a failing task still holding The Task lock, so trigger will not schedule The Task execution
        timeProvider.addSeconds(10);

        // Give time for mock executor to detect time change
        sleepASecond();

        // The Task Counter is unchanged
        Assert.assertEquals("Task counter should be unchanged at 5", 5, theTaskCounter.get());

        // But The Trigger Counter should have increased
        Assert.assertEquals("Trigger Task has been executed twice", 2, regularTriggerCounter.get());

        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 9);
        Assert.assertEquals("Entry is of type " + MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE,
                MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, elog.get(8).type);

        log.info("Current time (5 - trigger): " + CurrentTime.currentTime());
        dumpExecutorLog(executor);
    }

    private void testSixthExecution(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter) {
        // Move 21 secs into the future to get closer to 32 seconds
        // which should trigger the sixth execution of The Task
        timeProvider.addSeconds(21);

        int expected = 6;
        waitForTargetCount(theTaskCounter, expected);

        // The Task should fail and a second of sleep will make sure we can detect re-scheduled task
        sleepASecond();

        // But this time there should be no additional scheduling of The Task
        // because this time it overshoots the cutoff limit of 60 seconds (it would be 64 seconds pause)
        List<MockScheduledExecutorLog> elog = executor.log();
        Assert.assertEquals("Still has 9 entries", 9, elog.size());

        // With taskDuration = 4 we should after 6 executions be at around 125 seconds (plus 60 seconds initial offset)
        // immediate 4s execution + 2 x (5s pause + 4s execution) + 8s pause + 4s execution + 16s pause + 4s + 32s pause + 4s
        log.info("Current time (6): " + CurrentTime.currentTime());
    }

    private void testThirdTriggerTask(MockCurrentTimeProvider timeProvider, MockTimeProviderBasedScheduledExecutorService executor, AtomicInteger theTaskCounter, AtomicInteger regularTriggerCounter) {
        // The Trigger Task has thus far been executed once
        Assert.assertEquals("Trigger Task has been executed twice", 2, regularTriggerCounter.get());

        // Move 38 secs into the future which should trigger The Trigger Task again
        timeProvider.addSeconds(38);

        int expected = 7;
        waitForTargetCount(theTaskCounter, expected);

        // Possibly wait some more for next fast execution task to be scheduled
        List<MockScheduledExecutorLog> elog = waitForEntryCount(executor, 11);
        assertLogEntry(elog.get(9), MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);

        Assert.assertEquals("Entry is of type " + MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, MockExecutorLogActionType.SCHEDULE_AT_FIXED_RATE, elog.get(10).type);
        dumpExecutorLog(executor);
    }

    private void dumpSchedules(Map<Long, Runnable> schedules) {
        log.debug("Schedules: ");
        for (Map.Entry<Long, Runnable> e : schedules.entrySet()) {
            log.debug("  - " + e.getKey());
        }
    }

    private void sleepASecond() {
        try {
            // Simulate the task taking some time
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted");
        }
    }

    private void dumpExecutorLog(MockScheduledExecutorService executor) {
        log.debug("Executor log: ");
        for (MockScheduledExecutorLog e : executor.invocationLog()) {
            log.debug("" + e);
        }
    }

    private void dumpExecutorLog(MockTimeProviderBasedScheduledExecutorService executor) {
        log.debug("Executor log: ");
        for (MockScheduledExecutorLog e : executor.log()) {
            log.debug("" + e);
        }
    }

    private void dumpScheduleSuccessLog(List<Boolean> scheduleSuccess) {
        log.debug("Schedule success log: " + scheduleSuccess);
    }

    private void waitForTargetCount(AtomicInteger counter, int expected) {
        for (int timeout = 10000; counter.get() < expected && timeout > 0; timeout--) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }
        if (counter.get() < expected) {
            Assert.fail("Target count hasn't reached " + expected + " within 5 secs");
        }
    }

    private List<MockScheduledExecutorLog> waitForEntryCount(MockTimeProviderBasedScheduledExecutorService executor, int expectedSize) {
        for (int timeout = 10000; executor.log().size() < expectedSize && timeout > 0; timeout--) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted", e);
            }
        }
        if (executor.log().size() < expectedSize) {
            Assert.fail("Executor log hasn't reached size " + expectedSize + " within 5 secs");
        }
        return executor.log();
    }

    @Test
    public void testFloodRequests() {
        MockScheduledExecutorService executor = new MockScheduledExecutorService();

        AtomicInteger counter = new AtomicInteger();

        Runnable task = () -> {
            counter.incrementAndGet();
            sleepASecond();
        };

        BackOffTaskScheduler scheduler = new BackOffTaskScheduler(executor, 1, 300, task);

        // Flood with schedule requests
        for (int i = 0; i < 100; i++) {
            scheduler.scheduleTask();
        }

        Assert.assertEquals("Executor log should have 1 entry", 1, executor.invocationLog().size());

        MockScheduledExecutorLog entry = executor.invocationLog().getFirst();
        assertLogEntry(entry, MockExecutorLogActionType.SCHEDULE, 0, TimeUnit.MILLISECONDS);
    }

    private static void assertLogEntry(MockScheduledExecutorLog entry, MockExecutorLogActionType type, int delayOrPeriod, TimeUnit delayUnit) {
        Assert.assertEquals("Entry is of type " + type, type, entry.type);
        Assert.assertEquals("DelayOrPeriod is " + delayOrPeriod, delayOrPeriod, entry.delayOrPeriod);
        Assert.assertEquals("DelayUnit is " + delayUnit, delayUnit, entry.delayUnit);
    }

    private static void printTitle(String message) {
        System.out.println("\n=== " + message + " ===\n");
    }
}
