package com.rapidswing.table;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A table model for a list of beans, with typed columns defined in code instead of a hand-written
 * {@code getValueAt} switch:
 * <pre>
 * BeanTableModel&lt;Customer&gt; model = BeanTableModel.builder(Customer.class)
 *         .column("ID", Integer.class, Customer::getId).width(60)
 *         .column("Name", String.class, Customer::getName).editable(Customer::setName)
 *         .column("Balance", BigDecimal.class, Customer::getBalance).format(money::format)
 *         .build();
 * model.setRows(customers);
 * </pre>
 * Like all Swing models, use it on the EDT.
 */
public class BeanTableModel<T> extends AbstractTableModel {

    private final List<Column<T, ?>> columns;
    private final List<T> rows = new ArrayList<>();

    BeanTableModel(List<Column<T, ?>> columns) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    }

    /** The class argument only fixes the row type, so column getters can be method references. */
    public static <T> TableBuilder<T> builder(Class<T> rowType) {
        Objects.requireNonNull(rowType, "rowType");
        return new TableBuilder<>(new TableBuilder.State<>());
    }

    // ---- Rows ----------------------------------------------------------------------------------------

    public void setRows(Collection<? extends T> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public List<T> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /** The row at a <em>model</em> index. Convert view indexes with {@code table.convertRowIndexToModel}. */
    public T getRow(int modelIndex) {
        return rows.get(modelIndex);
    }

    public int indexOf(T row) {
        return rows.indexOf(row);
    }

    public void add(T row) {
        rows.add(row);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    public boolean remove(T row) {
        int index = rows.indexOf(row);
        if (index < 0) {
            return false;
        }
        rows.remove(index);
        fireTableRowsDeleted(index, index);
        return true;
    }

    /** Repaints a row after its bean was changed outside the table. Returns false if it is not in the model. */
    public boolean update(T row) {
        int index = rows.indexOf(row);
        if (index < 0) {
            return false;
        }
        fireTableRowsUpdated(index, index);
        return true;
    }

    /** Replaces a row in place, e.g. with the saved version of an edited copy. Returns false if not found. */
    public boolean replace(T oldRow, T newRow) {
        int index = rows.indexOf(oldRow);
        if (index < 0) {
            return false;
        }
        rows.set(index, newRow);
        fireTableRowsUpdated(index, index);
        return true;
    }

    public void clear() {
        rows.clear();
        fireTableDataChanged();
    }

    // ---- Columns -------------------------------------------------------------------------------------

    public List<Column<T, ?>> getColumns() {
        return columns;
    }

    public Column<T, ?> getColumn(int index) {
        return columns.get(index);
    }

    // ---- TableModel ----------------------------------------------------------------------------------

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).getName();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).getType();
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).getValue(rows.get(row));
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return columns.get(column).isEditable();
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        columns.get(column).setValue(rows.get(row), value);
        fireTableCellUpdated(row, column);
    }
}
