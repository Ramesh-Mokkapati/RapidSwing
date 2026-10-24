package com.rapidswing.util;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/**
 * Helpers for working with the Swing Event Dispatch Thread (EDT).
 */
public final class Edt {

    private Edt() {
    }

    /** Runs the code immediately when already on the EDT, otherwise schedules it with {@code invokeLater}. */
    public static void run(Runnable code) {
        if (SwingUtilities.isEventDispatchThread()) {
            code.run();
        } else {
            SwingUtilities.invokeLater(code);
        }
    }

    /** Always schedules the code to run later on the EDT. */
    public static void later(Runnable code) {
        SwingUtilities.invokeLater(code);
    }

    /** Runs the code on the EDT and blocks the calling thread until it has finished. */
    public static void runAndWait(Runnable code) {
        if (SwingUtilities.isEventDispatchThread()) {
            code.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the EDT", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    /** Fails fast when called from any thread other than the EDT. */
    public static void checkEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must be called on the Event Dispatch Thread, but was called on '"
                    + Thread.currentThread().getName() + "'");
        }
    }
}
