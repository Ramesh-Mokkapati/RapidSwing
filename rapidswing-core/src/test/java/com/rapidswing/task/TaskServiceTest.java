package com.rapidswing.task;

import com.rapidswing.util.Edt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceTest {

    private final TaskService tasks = new TaskService();

    @AfterEach
    void shutdown() {
        tasks.shutdown();
    }

    @Test
    void workRunsInBackgroundAndSuccessOnEdt() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean workOnEdt = new AtomicBoolean(true);
        AtomicBoolean callbackOnEdt = new AtomicBoolean();
        AtomicReference<String> result = new AtomicReference<>();

        tasks.call(() -> {
            workOnEdt.set(SwingUtilities.isEventDispatchThread());
            return "value";
        }).onSuccess(value -> {
            callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
            result.set(value);
        }).onFinally(done::countDown).start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertFalse(workOnEdt.get());
        assertTrue(callbackOnEdt.get());
        assertEquals("value", result.get());
    }

    @Test
    void failureGoesToOnError() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("boom");
        AtomicReference<Throwable> received = new AtomicReference<>();

        tasks.run(() -> {
            throw failure;
        }).onError(received::set).onFinally(done::countDown).start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertSame(failure, received.get());
    }

    @Test
    void failureWithoutOnErrorGoesToDefaultHandler() throws InterruptedException {
        CountDownLatch handled = new CountDownLatch(1);
        tasks.setDefaultErrorHandler(e -> handled.countDown());

        tasks.run(() -> {
            throw new IllegalStateException("boom");
        }).start();

        assertTrue(handled.await(5, TimeUnit.SECONDS));
    }

    @Test
    void cancelledTaskSkipsSuccessButRunsFinally() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean();

        Task<String> task = tasks.call(() -> {
            started.countDown();
            Thread.sleep(10_000);
            return "too late";
        }).onSuccess(v -> succeeded.set(true)).onFinally(done::countDown).start();

        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(task.cancel());

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(task.isCancelled());
        assertTrue(task.isDone());
        assertFalse(succeeded.get());
    }

    @Test
    void runningCountTracksActiveTasks() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger countWhileRunning = new AtomicInteger(-1);

        tasks.call(() -> release.await(5, TimeUnit.SECONDS)).onFinally(done::countDown).start();
        Edt.runAndWait(() -> countWhileRunning.set(tasks.runningCount().get()));
        release.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));

        AtomicInteger countAfter = new AtomicInteger(-1);
        Edt.runAndWait(() -> countAfter.set(tasks.runningCount().get()));
        assertEquals(1, countWhileRunning.get());
        assertEquals(0, countAfter.get());
    }

    @Test
    void taskCannotBeStartedTwice() {
        Task<Void> task = tasks.run(() -> { }).start();

        assertThrows(IllegalStateException.class, task::start);
        assertThrows(IllegalStateException.class, () -> task.onSuccess(v -> { }));
    }
}
