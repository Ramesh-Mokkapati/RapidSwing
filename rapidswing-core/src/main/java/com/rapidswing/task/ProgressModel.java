package com.rapidswing.task;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Progress} that a user interface can show: the worker reports from its thread, and the properties
 * change on the EDT. Updates are coalesced, so reporting thousands of steps does not flood the EDT.
 */
public class ProgressModel implements Progress {

    private final Property<String> message = new Property<>("");
    private final Property<Integer> done = new Property<>(0);
    private final Property<Integer> total = new Property<>(0);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean updateQueued = new AtomicBoolean();

    private volatile String currentMessage = "";
    private volatile int currentDone;
    private volatile int currentTotal;

    @Override
    public void begin(String text, int totalSteps) {
        currentMessage = text == null ? "" : text;
        currentTotal = Math.max(0, totalSteps);
        currentDone = 0;
        publish();
    }

    @Override
    public void step(String text) {
        if (text != null) {
            currentMessage = text;
        }
        currentDone++;
        publish();
    }

    @Override
    public void worked(int steps) {
        currentDone += steps;
        publish();
    }

    @Override
    public void message(String text) {
        currentMessage = text == null ? "" : text;
        publish();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Asks the work to stop; it does so at its next {@link #checkCancelled()}. */
    public void cancel() {
        cancelled.set(true);
    }

    /** What the work is doing. EDT only. */
    public Property<String> message() {
        return message;
    }

    /** Steps done so far. EDT only. */
    public Property<Integer> done() {
        return done;
    }

    /** Total steps; 0 while unknown. EDT only. */
    public Property<Integer> total() {
        return total;
    }

    private void publish() {
        if (updateQueued.compareAndSet(false, true)) {
            Edt.run(() -> {
                updateQueued.set(false);
                total.set(currentTotal);
                done.set(Math.min(currentDone, currentTotal > 0 ? currentTotal : currentDone));
                message.set(currentMessage);
            });
        }
    }
}
