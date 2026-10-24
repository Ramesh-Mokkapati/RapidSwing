package com.rapidswing.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Defines the columns of a {@link BeanTableModel}. Each {@link #column} call returns a {@link ColumnBuilder}
 * for configuring that column, which can also add the next column.
 */
public class TableBuilder<T> {

    /** State shared by a table builder and all of its column builders. */
    static final class State<T> {
        final List<Column<T, ?>> columns = new ArrayList<>();
        boolean built;
    }

    final State<T> state;

    TableBuilder(State<T> state) {
        this.state = state;
    }

    /**
     * @param type the value type, used for sorting and default rendering; use {@code Integer.class} rather
     *             than {@code int.class} for primitive getters
     */
    public <V> ColumnBuilder<T, V> column(String name, Class<V> type, Function<? super T, ? extends V> getter) {
        checkNotBuilt();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(getter, "getter");
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Use the wrapper class instead of " + type + " for column '" + name + "'");
        }
        for (Column<T, ?> existing : state.columns) {
            if (existing.getName().equals(name)) {
                throw new IllegalArgumentException("The table already has a column named '" + name + "'");
            }
        }
        Column<T, V> column = new Column<>(name, type, getter);
        state.columns.add(column);
        return new ColumnBuilder<>(state, column);
    }

    public BeanTableModel<T> build() {
        checkNotBuilt();
        if (state.columns.isEmpty()) {
            throw new IllegalStateException("A table needs at least one column");
        }
        state.built = true;
        return new BeanTableModel<>(state.columns);
    }

    void checkNotBuilt() {
        if (state.built) {
            throw new IllegalStateException("The table model has already been built");
        }
    }
}
