package com.rapidswing.table;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.data.Query;
import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Subscription;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A ready-to-use table for a {@link BeanTableModel}: search box, sortable columns, row count,
 * column chooser (right-click the header), selection properties, open-on-double-click/Enter,
 * empty and loading messages, CSV export and printing, row actions with selection rules and a right-click
 * menu ({@link #rowAction}), cell styles ({@link #rowStyle}, {@link ColumnBuilder#style}) and comparing rows.
 * <pre>
 * DataGrid&lt;Customer&gt; grid = new DataGrid&lt;&gt;(model)
 *         .addAction(refreshAction)
 *         .onOpen(this::edit);
 * grid.selectedItem().onChange(customer -&gt; ...);
 * grid.setRows(customers);
 * </pre>
 * Like all Swing components, use it on the EDT.
 */
public class DataGrid<T> extends JPanel {

    private static final String OPEN_ACTION = "rapidswing.open";

    private final BeanTableModel<T> model;
    private final GridTable table;
    private final TableRowSorter<BeanTableModel<T>> sorter;
    private final JTextField search = new JTextField(16);
    private final JLabel count = new JLabel();
    private final JToolBar actions = new JToolBar();
    private final List<TableColumn> allColumns = new ArrayList<>();
    private final boolean[] visible;

    private final Property<T> selectedItem = new Property<>();
    private final Property<List<T>> selectedItems = new Property<>(Collections.<T>emptyList());

    private Consumer<? super T> openHandler;
    private String emptyText;
    private Subscription localeSubscription;
    private boolean loading;
    private boolean restoringSelection;
    private final Map<RowAction<T>, JButton> rowActions = new LinkedHashMap<>();
    private Function<? super T, CellStyle> rowStyle;
    private BiPredicate<String, String> confirmHandler = (question, title) -> JOptionPane.showConfirmDialog(this,
            question, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;

    public DataGrid(BeanTableModel<T> model) {
        super(new BorderLayout(0, 6));
        this.model = Objects.requireNonNull(model, "model");
        this.visible = new boolean[model.getColumnCount()];

        table = new GridTable(model);
        table.setAutoCreateColumnsFromModel(false);
        table.setFillsViewportHeight(true);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        configureColumns();

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !restoringSelection) {
                updateSelection();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && table.rowAtPoint(e.getPoint()) >= 0) {
                    openSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowRowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowRowMenu(e);
            }
        });
        selectedItems.onChange(items -> rowActions.keySet().forEach(RowAction::update));
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowColumnMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowColumnMenu(e);
            }
        });

        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        sorter.addRowSorterListener(e -> updateCount());
        model.addTableModelListener(e -> updateCount());

        actions.setFloatable(false);
        actions.setBorder(BorderFactory.createEmptyBorder());
        // A box layout lets the search field shrink instead of overlapping long (e.g. translated) action names.
        search.setMaximumSize(search.getPreferredSize());
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.add(actions);
        top.add(Box.createHorizontalGlue());
        top.add(Box.createHorizontalStrut(8));
        top.add(search);
        // The count sits under the table, where its changing width cannot push other controls around.
        count.setHorizontalAlignment(SwingConstants.RIGHT);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(count, BorderLayout.SOUTH);
        updateTexts();
    }

    // ---- Configuration -------------------------------------------------------------------------------

    /** Adds a button to the grid's toolbar, left of the search box. */
    public DataGrid<T> addAction(Action action) {
        actions.add(action);
        return this;
    }

    /**
     * Called with the row the user opens by double-clicking it or pressing Enter.
     * Avoid combining with editable columns, which also react to double-clicks.
     */
    public DataGrid<T> onOpen(Consumer<? super T> handler) {
        this.openHandler = Objects.requireNonNull(handler, "handler");
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), OPEN_ACTION);
        table.getActionMap().put(OPEN_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelected();
            }
        });
        return this;
    }

    /**
     * Adds an action on the selected rows, shown in the toolbar and the right-click menu. Configure it with the
     * returned {@link RowAction}: selection rule, row condition, confirmation, permission, shortcut.
     */
    public RowAction<T> rowAction(String name, Consumer<? super List<T>> handler) {
        RowAction<T> action = new RowAction<>(this, name, handler);
        JButton button = actions.add(action);
        button.setFocusable(false);
        rowActions.put(action, button);
        action.update();
        rowActionsChanged();
        return action;
    }

    /** The row actions, in the order they were added. */
    public List<RowAction<T>> rowActions() {
        return new ArrayList<>(rowActions.keySet());
    }

    /** A "Compare" action for two or more rows: shows them side by side with the differences marked. */
    public RowAction<T> addCompareAction() {
        return rowAction(I18n.global().get("grid.compare"), rows -> CompareDialog.show(this, model, rows))
                .selection(RowAction.Selection.TWO_OR_MORE);
    }

    /** Styles whole rows, e.g. overdue orders; column styles are applied on top. Return {@code null} for none. */
    public DataGrid<T> rowStyle(Function<? super T, CellStyle> styler) {
        this.rowStyle = styler;
        table.repaint();
        return this;
    }

    /** Replaces the yes/no question of row actions, e.g. with scripted answers in tests: (question, title). */
    public DataGrid<T> setConfirmHandler(BiPredicate<String, String> handler) {
        this.confirmHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    BiPredicate<String, String> confirmHandler() {
        return confirmHandler;
    }

    /** Shows the toolbar buttons of row actions that are permitted and not menu-only. */
    void rowActionsChanged() {
        rowActions.forEach((action, button) -> button.setVisible(action.isInToolbar() && action.isPermitted()));
    }

    void bindRowActionKey(KeyStroke stroke, RowAction<T> action) {
        String key = "rapidswing.rowAction." + stroke;
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, key);
        table.getActionMap().put(key, action);
    }

    /**
     * The sort order the user chose by clicking column headers, for columns with a {@link ColumnBuilder#field}.
     * Use it to load pages sorted by the server; the grid also sorts the loaded rows itself.
     */
    public List<Query.Sort> sortOrder() {
        List<Query.Sort> sorts = new ArrayList<>();
        for (RowSorter.SortKey key : sorter.getSortKeys()) {
            String field = model.getColumn(key.getColumn()).getField();
            if (field != null && key.getSortOrder() != SortOrder.UNSORTED) {
                sorts.add(new Query.Sort(field, key.getSortOrder() == SortOrder.ASCENDING));
            }
        }
        return sorts;
    }

    /** Called when the user changes the sort order, e.g. to reload a page sorted by the server. */
    public DataGrid<T> onSortChanged(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
                handler.run();
            }
        });
        return this;
    }

    /** Message shown when there are no rows at all. Defaults to the translated "No data". */
    public DataGrid<T> emptyText(String text) {
        this.emptyText = text;
        table.repaint();
        return this;
    }

    /** Shows "Loading..." instead of the empty message, e.g. while a background task fetches the rows. */
    public void setLoading(boolean loading) {
        this.loading = loading;
        table.repaint();
    }

    // ---- Rows and selection --------------------------------------------------------------------------

    /** Replaces all rows, keeping the selection on rows that are still present (by {@code equals}). */
    public void setRows(Collection<? extends T> rows) {
        List<T> previous = selectedItems.get();
        restoringSelection = true;
        try {
            model.setRows(rows);
            for (T item : previous) {
                int viewIndex = viewIndexOf(item);
                if (viewIndex >= 0) {
                    table.addRowSelectionInterval(viewIndex, viewIndex);
                }
            }
        } finally {
            restoringSelection = false;
        }
        updateSelection();
    }

    /** The first selected row, or {@code null}. */
    public Property<T> selectedItem() {
        return selectedItem;
    }

    /** All selected rows, in view order. */
    public Property<List<T>> selectedItems() {
        return selectedItems;
    }

    /** Selects the row and scrolls to it. Returns false if it is not visible (missing or filtered out). */
    public boolean select(T item) {
        int viewIndex = viewIndexOf(item);
        if (viewIndex < 0) {
            return false;
        }
        table.setRowSelectionInterval(viewIndex, viewIndex);
        table.scrollRectToVisible(table.getCellRect(viewIndex, 0, true));
        return true;
    }

    /** The rows currently shown, after search and sorting. */
    public List<T> visibleRows() {
        List<T> rows = new ArrayList<>(table.getRowCount());
        for (int viewIndex = 0; viewIndex < table.getRowCount(); viewIndex++) {
            rows.add(model.getRow(table.convertRowIndexToModel(viewIndex)));
        }
        return rows;
    }

    // ---- Search and columns --------------------------------------------------------------------------

    public void setSearchText(String text) {
        search.setText(text);
    }

    public boolean isColumnVisible(String name) {
        return visible[columnIndex(name)];
    }

    /** Shows or hides a column. The last visible column cannot be hidden. */
    public void setColumnVisible(String name, boolean show) {
        int index = columnIndex(name);
        if (visible[index] == show) {
            return;
        }
        TableColumnModel columns = table.getColumnModel();
        if (show) {
            columns.addColumn(allColumns.get(index));
            int position = 0;
            for (int i = 0; i < index; i++) {
                if (visible[i]) {
                    position++;
                }
            }
            columns.moveColumn(columns.getColumnCount() - 1, Math.min(position, columns.getColumnCount() - 1));
        } else {
            if (columns.getColumnCount() == 1) {
                throw new IllegalStateException("The last visible column cannot be hidden");
            }
            columns.removeColumn(allColumns.get(index));
        }
        visible[index] = show;
        applyFilter();
    }

    // ---- Export --------------------------------------------------------------------------------------

    /** Writes the visible rows and columns, as displayed, in CSV format (RFC 4180). */
    public void exportCsv(Writer out) throws IOException {
        TableColumnModel columns = table.getColumnModel();
        List<Column<T, ?>> exported = new ArrayList<>();
        List<String> header = new ArrayList<>();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            Column<T, ?> column = model.getColumn(columns.getColumn(i).getModelIndex());
            exported.add(column);
            header.add(column.getName());
        }
        writeCsvLine(out, header);
        for (T row : visibleRows()) {
            List<String> cells = new ArrayList<>(exported.size());
            for (Column<T, ?> column : exported) {
                cells.add(column.format(row));
            }
            writeCsvLine(out, cells);
        }
        out.flush();
    }

    /** Writes a UTF-8 CSV file with a byte order mark, so Excel detects the encoding. */
    public void exportCsv(Path file) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write('﻿');
            exportCsv(out);
        }
    }

    // ---- Printing ------------------------------------------------------------------------------------

    /**
     * Prints the visible rows and columns, fitted to the page width, with the title on top and page numbers
     * below. Shows the print dialog; returns false if the user cancelled it.
     */
    public boolean print(String title) throws PrinterException {
        return table.print(JTable.PrintMode.FIT_WIDTH, headerFormat(title), footerFormat());
    }

    /** What {@link #print} prints, e.g. for a print preview or to print without a dialog. */
    public Printable printable(String title) {
        return table.getPrintable(JTable.PrintMode.FIT_WIDTH, headerFormat(title), footerFormat());
    }

    /** The title as a literal message pattern: quotes and braces are escaped. */
    static MessageFormat headerFormat(String title) {
        return title == null ? null : new MessageFormat(title.replace("'", "''").replace("{", "'{'"));
    }

    private static MessageFormat footerFormat() {
        return new MessageFormat(I18n.global().get("grid.printFooter"));
    }

    // ---- Access to the parts -------------------------------------------------------------------------

    public JTable getTable() {
        return table;
    }

    public BeanTableModel<T> getModel() {
        return model;
    }

    public JTextField getSearchField() {
        return search;
    }

    // ---- Internals -----------------------------------------------------------------------------------

    /** Follows locale changes only while displayed, so discarded grids are not kept alive by I18n. */
    @Override
    public void addNotify() {
        super.addNotify();
        if (localeSubscription == null) {
            localeSubscription = I18n.global().onChange(this::updateTexts);
        }
        updateTexts();
    }

    @Override
    public void removeNotify() {
        if (localeSubscription != null) {
            localeSubscription.unsubscribe();
            localeSubscription = null;
        }
        super.removeNotify();
    }

    private void updateTexts() {
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, I18n.global().get("grid.search"));
        updateCount();
    }

    private void configureColumns() {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < model.getColumnCount(); i++) {
            TableColumn tableColumn = columns.getColumn(i);
            Column<T, ?> column = model.getColumn(i);
            if (column.width > 0) {
                tableColumn.setPreferredWidth(column.width);
            }
            if (column.hasCustomRendering()) {
                tableColumn.setCellRenderer(new FormattingRenderer(column));
            }
            allColumns.add(tableColumn);
            visible[i] = true;
        }
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (model.getColumn(i).initiallyHidden) {
                setColumnVisible(model.getColumn(i).getName(), false);
            }
        }
    }

    private void applyFilter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<BeanTableModel<T>, Integer>() {
            @Override
            public boolean include(Entry<? extends BeanTableModel<T>, ? extends Integer> entry) {
                T row = model.getRow(entry.getIdentifier());
                for (int i = 0; i < visible.length; i++) {
                    if (visible[i] && model.getColumn(i).format(row).toLowerCase(Locale.ROOT).contains(query)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void updateSelection() {
        List<T> items = new ArrayList<>();
        for (int viewIndex : table.getSelectedRows()) {
            items.add(model.getRow(table.convertRowIndexToModel(viewIndex)));
        }
        selectedItems.set(Collections.unmodifiableList(items));
        selectedItem.set(items.isEmpty() ? null : items.get(0));
    }

    private void updateCount() {
        int total = model.getRowCount();
        int shown = table.getRowCount();
        I18n i18n = I18n.global();
        count.setText(shown == total ? i18n.get("grid.rows", total) : i18n.get("grid.rowsFiltered", shown, total));
        table.repaint();
    }

    private void openSelected() {
        T item = selectedItem.get();
        if (item != null && openHandler != null) {
            openHandler.accept(item);
        }
    }

    /** The style of a cell in view coordinates: row style with the column style on top; none while selected. */
    CellStyle styleAt(int viewRow, int viewColumn) {
        if (table.isRowSelected(viewRow)) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        CellStyle style = rowStyle == null ? null : rowStyle.apply(model.getRow(modelRow));
        CellStyle columnStyle = model.getColumn(modelColumn).styleFor(model.getValueAt(modelRow, modelColumn));
        if (columnStyle != null) {
            style = style == null ? columnStyle : style.overlay(columnStyle);
        }
        return style;
    }

    private int viewIndexOf(T item) {
        int modelIndex = model.indexOf(item);
        return modelIndex < 0 ? -1 : table.convertRowIndexToView(modelIndex);
    }

    private int columnIndex(String name) {
        for (int i = 0; i < model.getColumnCount(); i++) {
            if (model.getColumn(i).getName().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No column named '" + name + "'");
    }

    /** Right-click: selects the row under the mouse (unless it is part of the selection) and offers row actions. */
    private void maybeShowRowMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || rowActions.isEmpty()) {
            return;
        }
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow >= 0 && !table.isRowSelected(viewRow)) {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
        JPopupMenu menu = new JPopupMenu();
        for (RowAction<T> action : rowActions.keySet()) {
            if (action.isPermitted()) {
                menu.add(new JMenuItem(action));
            }
        }
        if (menu.getComponentCount() > 0) {
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    private void maybeShowColumnMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int visibleCount = table.getColumnModel().getColumnCount();
        JPopupMenu menu = new JPopupMenu();
        for (int i = 0; i < allColumns.size(); i++) {
            String name = model.getColumn(i).getName();
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, visible[i]);
            item.setEnabled(!(visible[i] && visibleCount == 1));
            item.addActionListener(event -> setColumnVisible(name, item.isSelected()));
            menu.add(item);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /** Paints the empty or loading message over an empty table. */
    private final class GridTable extends JTable {

        GridTable(BeanTableModel<T> model) {
            super(model);
        }

        /**
         * Applies row and column styles to unselected cells. A {@link DefaultTableCellRenderer} remembers colors
         * set on it as its "unselected" colors, so they are cleared first; otherwise one row's style would
         * stick to the rows painted after it.
         */
        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            if (renderer instanceof DefaultTableCellRenderer) {
                ((DefaultTableCellRenderer) renderer).setForeground(null);
                ((DefaultTableCellRenderer) renderer).setBackground(null);
            }
            Component cell = super.prepareRenderer(renderer, row, column);
            CellStyle style = styleAt(row, column);
            if (style != null) {
                if (style.getForeground() != null) {
                    cell.setForeground(style.getForeground());
                }
                if (style.getBackground() != null) {
                    cell.setBackground(style.getBackground());
                }
                if (style.isBold()) {
                    cell.setFont(cell.getFont().deriveFont(Font.BOLD));
                }
            }
            return cell;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() > 0) {
                return;
            }
            I18n i18n = I18n.global();
            String text = loading ? i18n.get("grid.loading")
                    : model.getRowCount() > 0 ? i18n.get("grid.noMatch")
                    : emptyText != null ? emptyText : i18n.get("grid.empty");
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if (hints instanceof Map) {
                    g2.addRenderingHints((Map<?, ?>) hints);
                }
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                Rectangle area = getVisibleRect();
                int x = area.x + (area.width - metrics.stringWidth(text)) / 2;
                int y = area.y + 24 + metrics.getAscent();
                g2.drawString(text, x, y);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Renders a column's formatted text; numbers are right-aligned unless the column says otherwise. */
    private static final class FormattingRenderer extends DefaultTableCellRenderer {
        private final Column<?, ?> column;

        FormattingRenderer(Column<?, ?> column) {
            this.column = column;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setText(column.formatValue(value));
            if (column.alignment >= 0) {
                setHorizontalAlignment(column.alignment);
            } else {
                setHorizontalAlignment(Number.class.isAssignableFrom(column.getType())
                        ? SwingConstants.RIGHT : SwingConstants.LEADING);
            }
            return this;
        }
    }

    private static void writeCsvLine(Writer out, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(csvEscape(cells.get(i)));
        }
        out.write("\r\n");
    }

    static String csvEscape(String text) {
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
