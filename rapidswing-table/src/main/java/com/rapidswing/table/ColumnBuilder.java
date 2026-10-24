package com.rapidswing.table;

import javax.swing.SwingConstants;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Configures the column just added to a {@link TableBuilder}. Inherits the builder's methods,
 * so the chain can continue with the next column.
 */
public final class ColumnBuilder<T, V> extends TableBuilder<T> {

    private final Column<T, V> column;

    ColumnBuilder(State<T> state, Column<T, V> column) {
        super(state);
        this.column = column;
    }

    /** Preferred width in pixels. */
    public ColumnBuilder<T, V> width(int pixels) {
        checkNotBuilt();
        column.width = pixels;
        return this;
    }

    /** Display text for non-null values, e.g. currency or date formatting. Also used by search and CSV export. */
    public ColumnBuilder<T, V> format(Function<? super V, String> formatter) {
        checkNotBuilt();
        column.formatter = Objects.requireNonNull(formatter, "formatter");
        return this;
    }

    /** Styles cells by value, e.g. negative amounts in red; return {@code null} for the default look. */
    public ColumnBuilder<T, V> style(Function<? super V, CellStyle> styler) {
        checkNotBuilt();
        column.style = Objects.requireNonNull(styler, "styler");
        return this;
    }

    /** {@link SwingConstants#LEFT}, {@link SwingConstants#CENTER} or {@link SwingConstants#RIGHT}. */
    public ColumnBuilder<T, V> align(int alignment) {
        checkNotBuilt();
        column.alignment = alignment;
        return this;
    }

    /**
     * Makes the column editable in the table. Swing's default editors handle {@code String}, numbers
     * and {@code Boolean}; other types need a custom cell editor on the table.
     */
    public ColumnBuilder<T, V> editable(BiConsumer<? super T, ? super V> setter) {
        checkNotBuilt();
        column.setter = Objects.requireNonNull(setter, "setter");
        return this;
    }

    /**
     * The property behind the column, e.g. {@code "name"}: lets {@link DataGrid#sortOrder()} report sorting by
     * this column, so pages loaded from a server are sorted there.
     */
    public ColumnBuilder<T, V> field(String propertyName) {
        checkNotBuilt();
        column.field = Objects.requireNonNull(propertyName, "propertyName");
        return this;
    }

    /** Starts hidden in a {@link DataGrid}; users can show it from the header's right-click menu. */
    public ColumnBuilder<T, V> hidden() {
        checkNotBuilt();
        column.initiallyHidden = true;
        return this;
    }
}
