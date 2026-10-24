package com.rapidswing.task;

import java.util.concurrent.CancellationException;

/**
 * What long-running work reports: steps done, what is happening, and
 * whether the user asked to stop. Call it from the worker thread.
 * <pre>
 * progress.begin("Importing", files.size());
 * for (Path file : files) {
 *     progress.checkCancelled();
 *     progress.step("Importing " + file.getFileName());
 *     importer.load(file);
 * }
 * </pre>
 */
public interface Progress {

    /** Starts counting: {@code totalSteps} of 0 or less means the amount of work is unknown. */
    void begin(String message, int totalSteps);

    /** One step done, now working on {@code message} (may be {@code null} to keep the current text). */
    void step(String message);

    /** Adds {@code steps} to the steps done. */
    void worked(int steps);

    void message(String message);

    boolean isCancelled();

    /** Throws {@link CancellationException} if the user cancelled. */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException();
        }
    }
}
