package com.rapidswing.table;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.data.Filter;
import com.rapidswing.data.Operator;
import com.rapidswing.i18n.I18n;
import com.rapidswing.ui.Formats;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A search form with an operator per field: "Name contains ber", "Balance
 * between 100 and 500", "Status is Active". Fields left empty do not filter. Searches are kept in a "Recent"
 * list for the session (shared by panels with the same id), to run again with one click.
 * <pre>
 * SearchPanel search = new SearchPanel("customers")
 *         .field("name", "Name", String.class)
 *         .field("status", "Status", Status.class)
 *         .field("balance", "Balance", BigDecimal.class)
 *         .onSearch(filters -&gt; load(Query.all().withFilters(filters)));
 * </pre>
 * Supported types: text, numbers, {@link LocalDate} (ISO {@code 2026-03-09} or the language's short format),
 * enums and booleans. Use it on the EDT.
 */
public class SearchPanel extends JPanel {

    private static final int MAX_HISTORY = 10;
    private static final Map<String, List<List<Filter>>> HISTORY = new ConcurrentHashMap<>();

    private final String id;
    private final List<FieldRow> rows = new ArrayList<>();
    private final JPanel fieldsPanel = new JPanel(new GridBagLayout());
    private final JComboBox<HistoryEntry> history = new JComboBox<>();
    private final JButton searchButton = new JButton();
    private final JButton clearButton = new JButton();
    private Consumer<? super List<Filter>> onSearch = filters -> { };
    private boolean restoringHistory;

    public SearchPanel(String id) {
        super(new java.awt.BorderLayout(0, 6));
        this.id = Objects.requireNonNull(id, "id");
        I18n i18n = I18n.global();
        searchButton.setText(i18n.get("search.search"));
        clearButton.setText(i18n.get("search.clear"));
        searchButton.addActionListener(e -> search());
        clearButton.addActionListener(e -> {
            clear();
            search();
        });
        history.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText(" ");
                }
                return this;
            }
        });
        history.addActionListener(e -> {
            HistoryEntry entry = (HistoryEntry) history.getSelectedItem();
            if (!restoringHistory && entry != null) {
                setFilters(entry.filters);
                search();
            }
        });
        JPanel bottom = new JPanel(new java.awt.BorderLayout(8, 0));
        JPanel recent = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        recent.add(new JLabel(i18n.get("search.history")));
        recent.add(history);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(clearButton);
        buttons.add(searchButton);
        bottom.add(recent, java.awt.BorderLayout.WEST);
        bottom.add(buttons, java.awt.BorderLayout.EAST);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        add(fieldsPanel, java.awt.BorderLayout.CENTER);
        add(bottom, java.awt.BorderLayout.SOUTH);
        refreshHistory();
    }

    /** Adds a search field: the field name used in {@link Filter}s, its label, and its type. */
    public SearchPanel field(String field, String label, Class<?> type) {
        FieldRow row = new FieldRow(field, label, boxed(type));
        int y = rows.size();
        rows.add(row);
        fieldsPanel.add(new JLabel(label), constraints(0, y, 0));
        fieldsPanel.add(row.operator, constraints(1, y, 0));
        fieldsPanel.add(row.value, constraints(2, y, 1));
        fieldsPanel.add(row.and, constraints(3, y, 0));
        fieldsPanel.add(row.secondValue, constraints(4, y, 1));
        row.updateValueFields();
        revalidate();
        return this;
    }

    /** Called with the filters when the user searches (Search button, Enter, Clear or a recent search). */
    public SearchPanel onSearch(Consumer<? super List<Filter>> handler) {
        this.onSearch = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Reads the form and, if every value is valid, remembers the search and calls the handler. Returns false
     * and marks the fields with invalid values otherwise.
     */
    public boolean search() {
        List<Filter> filters = new ArrayList<>();
        boolean valid = true;
        for (FieldRow row : rows) {
            try {
                row.clearError();
                Filter filter = row.filter();
                if (filter != null) {
                    filters.add(filter);
                }
            } catch (IllegalArgumentException e) {
                valid = false;
            }
        }
        if (!valid) {
            return false;
        }
        remember(filters);
        onSearch.accept(Collections.unmodifiableList(filters));
        return true;
    }

    /** The filters currently entered, without searching. Throws {@link IllegalArgumentException} for invalid values. */
    public List<Filter> getFilters() {
        List<Filter> filters = new ArrayList<>();
        for (FieldRow row : rows) {
            Filter filter = row.filter();
            if (filter != null) {
                filters.add(filter);
            }
        }
        return filters;
    }

    /** Fills the form from filters, e.g. a saved search. Fields without a filter are cleared. */
    public void setFilters(List<Filter> filters) {
        clear();
        for (Filter filter : filters) {
            for (FieldRow row : rows) {
                if (row.field.equals(filter.getField())) {
                    row.show(filter);
                }
            }
        }
    }

    public void clear() {
        rows.forEach(FieldRow::clear);
    }

    /** Recent searches of panels with this id, newest first. */
    public List<List<Filter>> history() {
        return new ArrayList<>(HISTORY.getOrDefault(id, Collections.emptyList()));
    }

    public JButton getSearchButton() {
        return searchButton;
    }

    /** The operator chooser of a field, e.g. for tests. */
    public JComboBox<Operator> operatorOf(String field) {
        return row(field).operator;
    }

    /** The (first) value editor of a field: a text field or a combo box. */
    public JComponent valueOf(String field) {
        return row(field).value;
    }

    public JComponent secondValueOf(String field) {
        return row(field).secondValue;
    }

    /** How a search reads in the "Recent" list, e.g. "Name contains ber, Status is Active". */
    public String describe(List<Filter> filters) {
        if (filters.isEmpty()) {
            return "*";
        }
        List<String> parts = new ArrayList<>();
        for (Filter filter : filters) {
            FieldRow row = rows.stream().filter(r -> r.field.equals(filter.getField())).findFirst().orElse(null);
            String label = row != null ? row.label : filter.getField();
            String text = label + " " + Formats.enumText(filter.getOperator());
            if (filter.getOperator().valueCount() >= 1) {
                text += " " + Formats.format(filter.getValue());
            }
            if (filter.getOperator().valueCount() == 2) {
                text += " " + I18n.global().get("search.and") + " " + Formats.format(filter.getSecondValue());
            }
            parts.add(text);
        }
        return String.join(", ", parts);
    }

    private FieldRow row(String field) {
        return rows.stream().filter(r -> r.field.equals(field)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No search field '" + field + "'"));
    }

    private void remember(List<Filter> filters) {
        if (filters.isEmpty()) {
            return;
        }
        List<List<Filter>> entries = HISTORY.computeIfAbsent(id, key -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (entries) {
            entries.remove(filters);
            entries.add(0, new ArrayList<>(filters));
            while (entries.size() > MAX_HISTORY) {
                entries.remove(entries.size() - 1);
            }
        }
        refreshHistory();
    }

    private void refreshHistory() {
        restoringHistory = true;
        try {
            DefaultComboBoxModel<HistoryEntry> model = new DefaultComboBoxModel<>();
            for (List<Filter> filters : history()) {
                model.addElement(new HistoryEntry(filters));
            }
            history.setModel(model);
            history.setSelectedItem(null);
            history.setEnabled(model.getSize() > 0);
        } finally {
            restoringHistory = false;
        }
    }

    private static GridBagConstraints constraints(int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(2, x == 0 ? 0 : 6, 2, 0);
        return c;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return type == short.class ? Short.class : type == byte.class ? Byte.class : Character.class;
    }

    private final class HistoryEntry {
        final List<Filter> filters;

        HistoryEntry(List<Filter> filters) {
            this.filters = filters;
        }

        @Override
        public String toString() {
            return describe(filters);
        }
    }

    /** One line of the form: operator, value, and the upper bound for "between". */
    private final class FieldRow {
        final String field;
        final String label;
        final Class<?> type;
        final JComboBox<Operator> operator;
        final JComponent value;
        final JComponent secondValue;
        final JLabel and = new JLabel(I18n.global().get("search.and"));

        FieldRow(String field, String label, Class<?> type) {
            this.field = Objects.requireNonNull(field, "field");
            this.label = label;
            this.type = type;
            this.operator = new JComboBox<>(Operator.forType(type));
            operator.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object item, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus);
                    setText(item == null ? "" : Formats.enumText((Operator) item));
                    return this;
                }
            });
            operator.addActionListener(e -> updateValueFields());
            this.value = editor();
            this.secondValue = editor();
        }

        private JComponent editor() {
            if (type.isEnum() || type == Boolean.class) {
                List<Object> choices = new ArrayList<>();
                choices.add(null);
                if (type == Boolean.class) {
                    choices.add(Boolean.TRUE);
                    choices.add(Boolean.FALSE);
                } else {
                    Collections.addAll(choices, type.getEnumConstants());
                }
                JComboBox<Object> combo = new JComboBox<>(choices.toArray());
                combo.setRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList<?> list, Object item, int index,
                                                                  boolean isSelected, boolean cellHasFocus) {
                        super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus);
                        setText(item == null ? " " : item instanceof Boolean
                                ? I18n.global().get((Boolean) item ? "search.yes" : "search.no") : Formats.format(item));
                        return this;
                    }
                });
                return combo;
            }
            JTextField text = new JTextField(12);
            text.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
            if (type == LocalDate.class) {
                text.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, I18n.global().get("form.datePlaceholder"));
            }
            text.addActionListener(e -> search());
            return text;
        }

        void updateValueFields() {
            int count = ((Operator) operator.getSelectedItem()).valueCount();
            value.setVisible(count >= 1);
            and.setVisible(count == 2);
            secondValue.setVisible(count == 2);
        }

        /** The row's filter, {@code null} if nothing is entered. */
        Filter filter() {
            Operator op = (Operator) operator.getSelectedItem();
            if (op.valueCount() == 0) {
                return Filter.of(field, op);
            }
            Object first = read(value);
            if (op.valueCount() == 1) {
                return first == null ? null : Filter.of(field, op, first);
            }
            Object second = read(secondValue);
            if (first == null && second == null) {
                return null;
            }
            if (first == null) {
                return Filter.of(field, Operator.LESS_OR_EQUAL, second);
            }
            if (second == null) {
                return Filter.of(field, Operator.GREATER_OR_EQUAL, first);
            }
            return Filter.between(field, first, second);
        }

        private Object read(JComponent editor) {
            if (editor instanceof JComboBox) {
                return ((JComboBox<?>) editor).getSelectedItem();
            }
            String text = ((JTextField) editor).getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            Operator op = (Operator) operator.getSelectedItem();
            if (type == String.class || op.isTextOnly()) {
                return text;
            }
            try {
                return parse(text);
            } catch (RuntimeException e) {
                editor.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
                editor.setToolTipText(I18n.global().get("search.invalidValue", label, text));
                throw new IllegalArgumentException(text, e);
            }
        }

        private Object parse(String text) {
            if (type == LocalDate.class) {
                try {
                    return LocalDate.parse(text);
                } catch (DateTimeParseException e) {
                    return LocalDate.parse(text, DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                            .withLocale(I18n.global().getLocale()));
                }
            }
            if (type == LocalDateTime.class) {
                return LocalDate.parse(text).atStartOfDay();
            }
            if (Number.class.isAssignableFrom(type)) {
                BigDecimal number = parseNumber(text);
                if (type == BigDecimal.class) {
                    return number;
                }
                if (type == Integer.class) {
                    return number.intValueExact();
                }
                if (type == Long.class) {
                    return number.longValueExact();
                }
                if (type == Short.class) {
                    return number.shortValueExact();
                }
                if (type == BigInteger.class) {
                    return number.toBigIntegerExact();
                }
                if (type == Float.class) {
                    return number.floatValue();
                }
                return number.doubleValue();
            }
            return text;
        }

        private BigDecimal parseNumber(String text) {
            NumberFormat format = NumberFormat.getNumberInstance(I18n.global().getLocale());
            if (format instanceof DecimalFormat) {
                ((DecimalFormat) format).setParseBigDecimal(true);
            }
            ParsePosition position = new ParsePosition(0);
            Number parsed = format.parse(text, position);
            if (parsed == null || position.getIndex() != text.length()) {
                throw new NumberFormatException(text);
            }
            return parsed instanceof BigDecimal ? (BigDecimal) parsed : new BigDecimal(parsed.toString());
        }

        void show(Filter filter) {
            operator.setSelectedItem(filter.getOperator());
            write(value, filter.getValue());
            write(secondValue, filter.getSecondValue());
            updateValueFields();
        }

        private void write(JComponent editor, Object content) {
            if (editor instanceof JComboBox) {
                ((JComboBox<?>) editor).setSelectedItem(content);
            } else {
                ((JTextField) editor).setText(content == null ? "" : content instanceof LocalDate
                        ? content.toString() : content instanceof Number ? numberText((Number) content)
                        : content.toString());
            }
        }

        private String numberText(Number number) {
            NumberFormat format = NumberFormat.getNumberInstance(I18n.global().getLocale());
            format.setGroupingUsed(false);
            format.setMaximumFractionDigits(10);
            return format.format(number);
        }

        void clear() {
            clearError();
            operator.setSelectedIndex(0);
            write(value, null);
            write(secondValue, null);
            updateValueFields();
        }

        void clearError() {
            for (JComponent editor : new JComponent[]{value, secondValue}) {
                editor.putClientProperty(FlatClientProperties.OUTLINE, null);
                editor.setToolTipText(null);
            }
        }
    }

    /** For tests: forgets the recent searches of all panels. */
    static void clearAllHistory() {
        HISTORY.clear();
    }

    @Override
    public String toString() {
        return "SearchPanel[" + id + ": " + rows.stream().map(r -> r.field).collect(Collectors.joining(", ")) + "]";
    }
}
