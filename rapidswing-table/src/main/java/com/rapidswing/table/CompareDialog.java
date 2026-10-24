package com.rapidswing.table;

import com.rapidswing.i18n.I18n;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows rows side by side, one column per row and one line per field, with differing fields marked.
 * {@link DataGrid#addCompareAction()} opens it for the selected rows.
 */
public final class CompareDialog {

    private CompareDialog() {
    }

    /** The names of the columns whose displayed values differ between the rows. */
    public static <T> List<String> differences(BeanTableModel<T> model, List<T> rows) {
        List<String> names = new ArrayList<>();
        for (Column<T, ?> column : model.getColumns()) {
            if (differs(column, rows)) {
                names.add(column.getName());
            }
        }
        return names;
    }

    /** The comparison table: field names, then one column per row headed by its first column's text. */
    public static <T> JTable table(BeanTableModel<T> model, List<T> rows, boolean onlyDifferences) {
        CompareModel<T> compareModel = new CompareModel<>(model, rows);
        compareModel.setOnlyDifferences(onlyDifferences);
        JTable table = new JTable(compareModel);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable jTable, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                // Clear the colors of the previous cell; this renderer would otherwise keep them.
                setForeground(null);
                setBackground(null);
                super.getTableCellRendererComponent(jTable, value, isSelected, hasFocus, row, column);
                boolean different = compareModel.isDifferent(row);
                setFont(getFont().deriveFont(column == 0 || different ? Font.BOLD : Font.PLAIN));
                if (different && column > 0 && !isSelected && CellStyle.WARNING.getBackground() != null) {
                    setBackground(CellStyle.WARNING.getBackground());
                    setForeground(CellStyle.WARNING.getForeground());
                }
                return this;
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        return table;
    }

    public static <T> void show(Component owner, BeanTableModel<T> model, List<T> rows) {
        I18n i18n = I18n.global();
        Window window = owner instanceof Window ? (Window) owner : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, i18n.get("compare.title"), JDialog.ModalityType.APPLICATION_MODAL);
        JTable table = table(model, rows, false);
        @SuppressWarnings("unchecked")
        CompareModel<T> compareModel = (CompareModel<T>) table.getModel();
        JCheckBox onlyDifferences = new JCheckBox(i18n.get("compare.onlyDifferences"));
        onlyDifferences.addActionListener(e -> compareModel.setOnlyDifferences(onlyDifferences.isSelected()));
        JButton close = new JButton(i18n.get("sysinfo.close"));
        close.addActionListener(e -> dialog.dispose());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(onlyDifferences, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(Math.min(1000, 160 + 200 * rows.size()),
                Math.min(520, table.getRowHeight() * (model.getColumnCount() + 2))));
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }

    private static <T> boolean differs(Column<T, ?> column, List<T> rows) {
        Set<String> values = new HashSet<>();
        for (T row : rows) {
            values.add(column.format(row));
        }
        return values.size() > 1;
    }

    /** Fields as rows, compared items as columns. */
    static final class CompareModel<T> extends AbstractTableModel {
        private final BeanTableModel<T> model;
        private final List<T> items;
        private final List<Column<T, ?>> allFields;
        private final Set<Column<T, ?>> different = new HashSet<>();
        private List<Column<T, ?>> shown;

        CompareModel(BeanTableModel<T> model, List<T> items) {
            this.model = model;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.allFields = model.getColumns();
            for (Column<T, ?> column : allFields) {
                if (differs(column, items)) {
                    different.add(column);
                }
            }
            this.shown = allFields;
        }

        void setOnlyDifferences(boolean only) {
            List<Column<T, ?>> fields = new ArrayList<>();
            for (Column<T, ?> column : allFields) {
                if (!only || different.contains(column)) {
                    fields.add(column);
                }
            }
            shown = fields;
            fireTableDataChanged();
        }

        boolean isDifferent(int row) {
            return different.contains(shown.get(row));
        }

        @Override
        public int getRowCount() {
            return shown.size();
        }

        @Override
        public int getColumnCount() {
            return items.size() + 1;
        }

        /** Items are headed by their first text column (e.g. a name), or else their first column. */
        @Override
        public String getColumnName(int column) {
            if (column == 0) {
                return I18n.global().get("compare.field");
            }
            Column<T, ?> heading = allFields.isEmpty() ? null : allFields.get(0);
            for (Column<T, ?> field : allFields) {
                if (field.getType() == String.class) {
                    heading = field;
                    break;
                }
            }
            return heading == null ? String.valueOf(column) : heading.format(items.get(column - 1));
        }

        @Override
        public Object getValueAt(int row, int column) {
            Column<T, ?> field = shown.get(row);
            return column == 0 ? field.getName() : field.format(items.get(column - 1));
        }
    }
}
