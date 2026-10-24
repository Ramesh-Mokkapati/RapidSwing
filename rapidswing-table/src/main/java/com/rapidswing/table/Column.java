package com.rapidswing.table;

import com.rapidswing.ui.Formats;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One column of a {@link BeanTableModel}: how to read (and optionally write and format) a value of a row.
 * Created through {@link BeanTableModel#builder(Class)}.
 */
public final class Column<T, V> {

    private final String name;
    private final Class<V> type;
    private final Function<? super T, ? extends V> getter;
    BiConsumer<? super T, ? super V> setter;
    Function<? super V, String> formatter;
    Function<? super V, CellStyle> style;
    int width = -1;
    int alignment = -1;
    boolean initiallyHidden;
    String field;

    Column(String name, Class<V> type, Function<? super T, ? extends V> getter) {
        this.name = name;
        this.type = type;
        this.getter = getter;
    }

    public String getName() {
        return name;
    }

    /** Used for sorting and default rendering: numbers sort numerically and align right. */
    public Class<V> getType() {
        return type;
    }

    public V getValue(T row) {
        return getter.apply(row);
    }

    /** The property name used in queries, e.g. for sorting on the server; {@code null} if not set. */
    public String getField() {
        return field;
    }

    public boolean isEditable() {
        return setter != null;
    }

    /** Writes an edited value back to the row. The value must be of the column's type or {@code null}. */
    public void setValue(T row, Object value) {
        if (setter == null) {
            throw new IllegalStateException("Column '" + name + "' is not editable");
        }
        setter.accept(row, type.cast(value));
    }

    /** The text shown in the cell, also used for searching and CSV export. */
    public String format(T row) {
        return formatValue(getValue(row));
    }

    /** Formats a value of this column's type, as {@link #format(Object)} does for a row. */
    public String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        V typed = type.cast(value);
        return formatter != null ? formatter.apply(typed) : Formats.format(typed);
    }

    /** The column's style for a value, or {@code null}. */
    CellStyle styleFor(Object value) {
        return style == null || value == null ? null : style.apply(type.cast(value));
    }

    /** Enums, dates and registered {@link Formats} types are rendered as formatted text. */
    boolean hasCustomRendering() {
        return formatter != null || alignment >= 0 || Formats.handles(type);
    }
}
