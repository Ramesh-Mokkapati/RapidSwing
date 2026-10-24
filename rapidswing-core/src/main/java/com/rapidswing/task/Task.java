package com.rapidswing.task;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A unit of background work created by {@link TaskService}. Configure the callbacks, then call {@link #start()}.
 * <p>
 * The work runs on a background thread. All callbacks run on the EDT. {@code onFinally} always runs,
 * including after cancellation. {@code onSuccess} and {@code onError} do not run for a cancelled task.
 */
public final class Task<T> {

    private final TaskService service;
    private final Callable<T> work;
    private final AtomicBoolean finished = new AtomicBoolean();

    private Consumer<? super T> onSuccess = result -> { };
    private Consumer<? super Throwable> onError;
    private Runnable onFinally = () -> { };
    private final List<Runnable> extraFinally = new CopyOnWriteArrayList<>();

    private volatile Future<?> future;
    private volatile boolean cancelled;

    Task(TaskService service, Callable<T> work) {
        this.service = service;
        this.work = Objects.requireNonNull(work, "work");
    }

    public Task<T> onSuccess(Consumer<? super T> handler) {
        checkNotStarted();
        this.onSuccess = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public Task<T> onError(Consumer<? super Throwable> handler) {
        checkNotStarted();
        this.onError = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public Task<T> onFinally(Runnable handler) {
        checkNotStarted();
        this.onFinally = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Adds a handler that runs after {@code onFinally}, without replacing it. For helpers that wrap a task, such
     * as {@link com.rapidswing.ui.ProgressDialog}; may be called before or after {@link #start()}.
     */
    public Task<T> alsoFinally(Runnable handler) {
        extraFinally.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public Task<T> start() {
        checkNotStarted();
        if (cancelled) {
            return this;
        }
        service.taskStarted();
        future = service.executor().submit(this::execute);
        return this;
    }

    /**
     * Cancels the task and interrupts its thread. A task cancelled before {@link #start()} never runs.
     * Returns false if the task was not running.
     */
    public boolean cancel() {
        Future<?> running = future;
        if (running == null) {
            cancelled = true;
            return false;
        }
        if (finished.get()) {
            return false;
        }
        cancelled = true;
        running.cancel(true);
        finish(null, null);
        return true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isDone() {
        return finished.get();
    }

    private void execute() {
        T result = null;
        Throwable failure = null;
        try {
            result = work.call();
        } catch (Throwable t) {
            failure = t;
        }
        finish(result, failure);
    }

    private void finish(T result, Throwable failure) {
        if (finished.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> complete(result, failure));
        }
    }

    private void complete(T result, Throwable failure) {
        try {
            if (cancelled) {
                return;
            }
            if (failure == null) {
                onSuccess.accept(result);
            } else if (onError != null) {
                onError.accept(failure);
            } else {
                service.defaultErrorHandler().accept(failure);
            }
        } finally {
            try {
                onFinally.run();
            } finally {
                try {
                    extraFinally.forEach(Runnable::run);
                } finally {
                    service.taskFinished();
                }
            }
        }
    }

    private void checkNotStarted() {
        if (future != null) {
            throw new IllegalStateException("Task has already been started");
        }
    }
}
