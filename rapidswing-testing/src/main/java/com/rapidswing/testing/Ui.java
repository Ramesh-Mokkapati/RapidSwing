package com.rapidswing.testing;

import com.rapidswing.util.Edt;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Drives Swing from a test thread the way a user would. Every method runs its work on the EDT and then
 * {@link #flush()}es, so results of what it triggered (including {@code TestTasks.immediate()} callbacks)
 * are visible when it returns.
 * <pre>
 * Ui.type(find.field("Name"), "Asha");
 * Ui.click(find.button("Save"));
 * assertEquals("Asha saved", harness.status());
 * </pre>
 * Interactions fail with an {@link AssertionError} if the user could not do them, e.g. clicking a disabled
 * or hidden button.
 */
public final class Ui {

    /** Rounds of queued EDT events processed by {@link #flush()}; enough for callbacks that queue callbacks. */
    private static final int FLUSH_ROUNDS = 3;

    private Ui() {
    }

    /** Runs the code on the EDT, then processes the events it queued. */
    public static void onEdt(Runnable code) {
        Edt.runAndWait(code);
        flush();
    }

    /** Computes a value on the EDT, then processes the events the computation queued. */
    public static <T> T onEdt(Callable<T> code) {
        AtomicReference<T> result = new AtomicReference<>();
        onEdt(() -> {
            try {
                result.set(code.call());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return result.get();
    }

    /** Lets the EDT process everything queued so far, such as {@code invokeLater} callbacks. */
    public static void flush() {
        for (int i = 0; i < FLUSH_ROUNDS; i++) {
            Edt.runAndWait(() -> { });
        }
    }

    /** Waits up to 5 seconds for a condition checked on the EDT, e.g. for work on real background threads. */
    public static void waitUntil(String description, BooleanSupplier condition) {
        waitUntil(description, condition, 5000);
    }

    public static void waitUntil(String description, BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        AtomicBoolean met = new AtomicBoolean();
        while (true) {
            Edt.runAndWait(() -> met.set(condition.getAsBoolean()));
            if (met.get()) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out after " + timeoutMillis + " ms waiting until " + description);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting until " + description, e);
            }
        }
    }

    public static void click(AbstractButton button) {
        onEdt(() -> {
            assertUsable(button, "click");
            button.doClick(0);
        });
    }

    /** Replaces the field's text, as typing over a selection would. */
    public static void type(JTextComponent field, String text) {
        onEdt(() -> {
            assertUsable(field, "type into");
            if (!field.isEditable()) {
                throw new AssertionError("Cannot type into a read-only " + describe(field));
            }
            field.setText(text);
        });
    }

    public static void select(JComboBox<?> combo, Object item) {
        onEdt(() -> {
            assertUsable(combo, "select in");
            combo.setSelectedItem(item);
            if (!Objects.equals(combo.getSelectedItem(), item)) {
                throw new AssertionError("'" + item + "' is not one of the choices of " + describe(combo));
            }
        });
    }

    /** Checks or unchecks a check box or toggle button, clicking it only if needed. */
    public static void check(AbstractButton box, boolean selected) {
        onEdt(() -> {
            assertUsable(box, "check");
            if (box.isSelected() != selected) {
                box.doClick(0);
            }
        });
    }

    /**
     * Visible as far as the window: the component and all its parents are visible. Windows themselves are
     * ignored, so tests can use frames that are never shown on screen.
     */
    public static boolean isVisibleToUser(Component component) {
        for (Component c = component; c != null && !(c instanceof Window); c = c.getParent()) {
            if (!c.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static void assertUsable(Component component, String verb) {
        if (!isVisibleToUser(component)) {
            throw new AssertionError("Cannot " + verb + " hidden " + describe(component));
        }
        if (!component.isEnabled()) {
            throw new AssertionError("Cannot " + verb + " disabled " + describe(component));
        }
    }

    static String describe(Component component) {
        String text = component instanceof AbstractButton ? ((AbstractButton) component).getText()
                : component instanceof JTextComponent ? ((JTextComponent) component).getText() : null;
        String name = component.getClass().getSimpleName();
        return text == null ? name : name + " '" + text + "'";
    }
}
