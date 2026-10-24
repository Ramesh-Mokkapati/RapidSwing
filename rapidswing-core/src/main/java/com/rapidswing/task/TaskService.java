package com.rapidswing.task;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs work off the EDT and delivers results back on the EDT.
 * <pre>
 * tasks.call(service::loadCustomers)
 *      .onSuccess(table::setRows)
 *      .onError(e -&gt; dialogs.error("Could not load customers", e))
 *      .start();
 * </pre>
 */
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final ExecutorService executor;
    private final Property<Integer> runningCount = new Property<>(0);
    private volatile Consumer<? super Throwable> defaultErrorHandler = e -> log.error("Background task failed", e);

    public TaskService() {
        this(Executors.newCachedThreadPool(new DaemonThreadFactory()));
    }

    public TaskService(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public <T> Task<T> call(Callable<T> work) {
        return new Task<>(this, work);
    }

    public Task<Void> run(Runnable work) {
        Objects.requireNonNull(work, "work");
        return call(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Work that reports progress and can be cancelled, e.g. for {@link com.rapidswing.ui.ProgressDialog}.
     * Cancelling the task also marks the progress as cancelled, so the work can stop at the next step.
     */
    public <T> Task<T> callWithProgress(ProgressModel progress, ProgressCallable<T> work) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(work, "work");
        return call(() -> work.call(progress));
    }

    /** Number of started tasks that have not finished yet. Read and observe it on the EDT. */
    public Property<Integer> runningCount() {
        return runningCount;
    }

    /** Used for tasks that do not set their own {@code onError} handler. */
    public void setDefaultErrorHandler(Consumer<? super Throwable> handler) {
        this.defaultErrorHandler = Objects.requireNonNull(handler, "handler");
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    ExecutorService executor() {
        return executor;
    }

    Consumer<? super Throwable> defaultErrorHandler() {
        return defaultErrorHandler;
    }

    void taskStarted() {
        Edt.run(() -> runningCount.set(runningCount.get() + 1));
    }

    void taskFinished() {
        Edt.run(() -> runningCount.set(runningCount.get() - 1));
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "rapidswing-task-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
