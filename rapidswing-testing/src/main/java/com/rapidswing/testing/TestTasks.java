package com.rapidswing.testing;

import com.rapidswing.task.TaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task services for tests.
 */
public final class TestTasks {

    private TestTasks() {
    }

    /**
     * Runs background work immediately, on the thread that starts the task. Callbacks still arrive on the EDT
     * via {@code invokeLater}, so drive the code with {@link Ui} (which flushes the EDT) and every task has
     * finished, callbacks included, by the time the {@code Ui} call returns. No sleeps or polling needed.
     */
    public static TaskService immediate() {
        return new TaskService(new ImmediateExecutor());
    }

    private static final class ImmediateExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
