package com.rapidswing.table;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An action on the selected rows of a {@link DataGrid}: enabled only
 * for a fitting selection, optionally only for rows that meet a condition, optionally after a confirmation, in
 * the grid's toolbar and its right-click menu.
 * <pre>
 * grid.rowAction("Deactivate", customers -&gt; service.deactivate(customers))
 *         .selection(RowAction.Selection.ONE_OR_MORE)
 *         .when(Customer::isActive)
 *         .confirm(rows -&gt; "Deactivate " + rows.size() + " customers?");
 * </pre>
 * Use it on the EDT.
 */
public final class RowAction<T> extends AbstractAction {

    /** Which selections the action accepts. */
    public enum Selection {
        /** Enabled regardless of the selection; receives the selected rows (maybe none). */
        ANY,
        /** Exactly one row. */
        ONE,
        /** At least one row. */
        ONE_OR_MORE,
        /** At least two rows, e.g. to compare them. */
        TWO_OR_MORE
    }

    private final DataGrid<T> grid;
    private final Consumer<? super List<T>> handler;
    private Selection selection = Selection.ONE_OR_MORE;
    private Predicate<? super T> condition = row -> true;
    private Function<? super List<T>, String> confirmation;
    private Property<Boolean> permitted = new Property<>(true);
    private boolean inToolbar = true;

    RowAction(DataGrid<T> grid, String name, Consumer<? super List<T>> handler) {
        super(name);
        this.grid = grid;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public RowAction<T> selection(Selection rule) {
        this.selection = Objects.requireNonNull(rule, "rule");
        update();
        return this;
    }

    /** Enabled only if every selected row meets the condition. */
    public RowAction<T> when(Predicate<? super T> rowCondition) {
        this.condition = Objects.requireNonNull(rowCondition, "rowCondition");
        update();
        return this;
    }

    /** Asks before running, e.g. "Delete 3 customers?". */
    public RowAction<T> confirm(Function<? super List<T>, String> question) {
        this.confirmation = Objects.requireNonNull(question, "question");
        return this;
    }

    /** Asks before running with a fixed question. */
    public RowAction<T> confirm(String question) {
        Objects.requireNonNull(question, "question");
        return confirm(rows -> question);
    }

    /** Shown only while the permission is true, e.g. {@code session.anyRole("admin")}. */
    public RowAction<T> requires(Property<Boolean> permission) {
        this.permitted = Objects.requireNonNull(permission, "permission");
        permission.onChange(allowed -> {
            grid.rowActionsChanged();
            update();
        });
        grid.rowActionsChanged();
        update();
        return this;
    }

    /** A key that runs the action while the table has focus, e.g. {@code "DELETE"} or {@code "ctrl D"}. */
    public RowAction<T> shortcut(String keyStroke) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        if (stroke == null) {
            throw new IllegalArgumentException("Invalid key stroke: '" + keyStroke + "'");
        }
        putValue(ACCELERATOR_KEY, stroke);
        grid.bindRowActionKey(stroke, this);
        return this;
    }

    /** Only in the right-click menu, not in the toolbar. */
    public RowAction<T> menuOnly() {
        inToolbar = false;
        grid.rowActionsChanged();
        return this;
    }

    public boolean isPermitted() {
        return Boolean.TRUE.equals(permitted.get());
    }

    boolean isInToolbar() {
        return inToolbar;
    }

    /** Whether the action accepts these rows. */
    public boolean accepts(List<T> rows) {
        if (!isPermitted()) {
            return false;
        }
        switch (selection) {
            case ONE:
                if (rows.size() != 1) {
                    return false;
                }
                break;
            case ONE_OR_MORE:
                if (rows.isEmpty()) {
                    return false;
                }
                break;
            case TWO_OR_MORE:
                if (rows.size() < 2) {
                    return false;
                }
                break;
            default:
                break;
        }
        for (T row : rows) {
            if (!condition.test(row)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        run();
    }

    /** Runs the action on the current selection, if it accepts it and the user confirms. Returns whether it ran. */
    public boolean run() {
        List<T> rows = grid.selectedItems().get();
        if (!accepts(rows)) {
            return false;
        }
        if (confirmation != null
                && !grid.confirmHandler().test(confirmation.apply(rows), I18n.global().get("dialog.confirm"))) {
            return false;
        }
        handler.accept(rows);
        return true;
    }

    void update() {
        setEnabled(accepts(grid.selectedItems().get()));
    }
}
