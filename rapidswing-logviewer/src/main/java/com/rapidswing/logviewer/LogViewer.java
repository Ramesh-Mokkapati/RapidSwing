package com.rapidswing.logviewer;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;
import com.rapidswing.table.BeanTableModel;
import com.rapidswing.task.TaskService;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A log viewer: open several log files (or drop them on the viewer) and see their entries merged in time order.
 * Filter by minimum level, search with plain text or regular expressions, read the full entry (including stack
 * traces) with matches highlighted, and follow growing files.
 * <p>
 * Formats are detected per file (Spring Boot, Logback/Log4j, SLF4J Simple, plain text), or chosen explicitly,
 * including custom regular expressions with named groups (see {@link LogFormat}).
 * <pre>
 * LogViewer viewer = new LogViewer(ctx.tasks());
 * viewer.open(Paths.get("logs/app.log"), Paths.get("logs/worker.log"));
 * </pre>
 * It is a view, so it can be registered with the {@code ViewManager}, or embedded as a panel.
 */
public class LogViewer extends AbstractView {

    private static final Object AUTO_FORMAT = new Object();
    private static final Object CUSTOM_FORMAT = new Object();
    private static final Object ALL_LEVELS = new Object();
    private static final Comparator<LogEntry> TIME_ORDER = Comparator
            .comparing((LogEntry entry) -> entry.sortTime, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparingLong(LogEntry::getSequence);
    private static final Color HIGHLIGHT = new Color(255, 196, 0, 140);

    private final TaskService tasks;
    private final I18n i18n = I18n.global();
    private final List<LogFile> files = new ArrayList<>();
    private final BeanTableModel<LogEntry> model;
    private final JTable table;
    private final TableRowSorter<BeanTableModel<LogEntry>> sorter;
    private final JComboBox<Object> formatBox = new JComboBox<>();
    private final JComboBox<Object> levelBox = new JComboBox<>();
    private final JTextField search = new JTextField(18);
    private final JCheckBox regexBox;
    private final JCheckBox caseBox;
    private final JToggleButton followButton;
    private final JTextArea detail = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final Timer followTimer = new Timer(1000, e -> refresh());

    private LogFormat chosenFormat;
    private Object lastFormatItem = AUTO_FORMAT;
    private LogLevel minimumLevel = LogLevel.TRACE;
    private Pattern searchPattern;
    private boolean reading;
    private boolean settingSearch;
    private String loadError;

    public LogViewer(TaskService tasks) {
        super(I18n.global().get("logviewer.title"));
        this.tasks = Objects.requireNonNull(tasks, "tasks");

        model = BeanTableModel.builder(LogEntry.class)
                .column(text("logviewer.column.time"), String.class, LogEntry::getTimestamp)
                .column(text("logviewer.column.level"), LogLevel.class, LogEntry::getLevel)
                .column(text("logviewer.column.file"), String.class, LogEntry::getSource)
                .column(text("logviewer.column.thread"), String.class, LogEntry::getThread)
                .column(text("logviewer.column.logger"), String.class, LogEntry::getLogger)
                .column(text("logviewer.column.message"), String.class, LogEntry::getSummary)
                .build();
        table = new EmptyMessageTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(1).setCellRenderer(new LevelRenderer());
        int[] widths = {170, 60, 110, 100, 180, 520};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        sorter.setRowFilter(new EntryFilter());
        sorter.addRowSorterListener(e -> updateStatus());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });

        formatBox.addItem(AUTO_FORMAT);
        for (LogFormat format : LogFormat.presets()) {
            formatBox.addItem(format);
        }
        formatBox.addItem(CUSTOM_FORMAT);
        formatBox.setRenderer(new LabelRenderer());
        formatBox.addActionListener(e -> formatChosen());

        levelBox.addItem(ALL_LEVELS);
        for (LogLevel level : Arrays.asList(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)) {
            levelBox.addItem(level);
        }
        levelBox.setRenderer(new LabelRenderer());
        levelBox.addActionListener(e -> {
            Object item = levelBox.getSelectedItem();
            setMinimumLevel(item instanceof LogLevel ? (LogLevel) item : LogLevel.TRACE);
        });

        regexBox = new JCheckBox(text("logviewer.regex"));
        caseBox = new JCheckBox(text("logviewer.matchCase"));
        followButton = new JToggleButton(text("logviewer.follow"));
        followButton.addActionListener(e -> setFollow(followButton.isSelected()));
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text("logviewer.search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchChanged();
            }
        });
        regexBox.addActionListener(e -> searchChanged());
        caseBox.addActionListener(e -> searchChanged());

        detail.setEditable(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, table.getFont().getSize()));

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.add(new JButton(AppAction.of(text("logviewer.open"), this::chooseFiles)));
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(new JButton(AppAction.of(text("logviewer.closeAll"), this::closeAll)));
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(new JLabel(text("logviewer.format")));
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(fixedWidth(formatBox));
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(new JLabel(text("logviewer.level")));
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(fixedWidth(levelBox));
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(fixedWidth(search));
        toolbar.add(regexBox);
        toolbar.add(caseBox);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(followButton);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.7);
        split.setBorder(null);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(toolbar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        FileDropHandler drop = new FileDropHandler();
        setTransferHandler(drop);
        table.setTransferHandler(drop);
        updateStatus();
    }

    // ---- API -----------------------------------------------------------------------------------------

    /** Opens log files in the background. Files already open are ignored. */
    public void open(Path... paths) {
        List<LogFile> added = new ArrayList<>();
        for (Path path : paths) {
            if (!isOpen(path)) {
                LogFile file = new LogFile(path, chosenFormat);
                files.add(file);
                added.add(file);
            }
        }
        if (!added.isEmpty()) {
            read(added);
        }
    }

    public void closeAll() {
        files.clear();
        model.clear();
        loadError = null;
        updateStatus();
    }

    /** Uses this format for all files; {@code null} detects the format per file. Files are read again. */
    public void setFormat(LogFormat format) {
        chosenFormat = format;
        List<Path> paths = new ArrayList<>();
        for (LogFile file : files) {
            paths.add(file.getPath());
        }
        closeAll();
        open(paths.toArray(new Path[0]));
    }

    public void setMinimumLevel(LogLevel level) {
        minimumLevel = Objects.requireNonNull(level, "level");
        sorter.allRowsChanged();
    }

    /** Sets the search text, as if typed. */
    public void setSearch(String text, boolean regex, boolean matchCase) {
        regexBox.setSelected(regex);
        caseBox.setSelected(matchCase);
        // setText is a remove plus an insert; apply the search once, not the empty text in between.
        settingSearch = true;
        try {
            search.setText(text);
        } finally {
            settingSearch = false;
        }
        searchChanged();
    }

    /** Re-reads the files every second, showing new entries as they are written. */
    public void setFollow(boolean follow) {
        followButton.setSelected(follow);
        if (follow) {
            followTimer.start();
        } else {
            followTimer.stop();
        }
    }

    /** Reads what was appended to the open files since the last read. */
    public void refresh() {
        if (!reading && !files.isEmpty()) {
            read(new ArrayList<>(files));
        }
    }

    /** The entries passing the current filters, in display order. */
    public List<LogEntry> visibleEntries() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            entries.add(model.getRow(table.convertRowIndexToModel(i)));
        }
        return entries;
    }

    public JTable getTable() {
        return table;
    }

    public String getDetailText() {
        return detail.getText();
    }

    public String getStatusText() {
        return status.getText();
    }

    @Override
    public void removeNotify() {
        followTimer.stop();
        super.removeNotify();
    }

    // ---- Reading -------------------------------------------------------------------------------------

    private void read(List<LogFile> toRead) {
        reading = true;
        tasks.call(() -> {
            Map<LogFile, LogFile.Update> updates = new LinkedHashMap<>();
            for (LogFile file : toRead) {
                updates.put(file, file.read());
            }
            return updates;
        }).onSuccess(this::apply)
                .onError(e -> {
                    loadError = text("logviewer.loadFailed", e instanceof IOException ? e.toString() : e.getMessage());
                    updateStatus();
                })
                .onFinally(() -> reading = false)
                .start();
    }

    private void apply(Map<LogFile, LogFile.Update> updates) {
        loadError = null;
        boolean atEnd = isScrolledToEnd();
        List<LogEntry> rows = new ArrayList<>(model.getRows());
        List<LogEntry> appended = new ArrayList<>();
        boolean rebuild = false;
        for (Map.Entry<LogFile, LogFile.Update> item : updates.entrySet()) {
            LogFile.Update update = item.getValue();
            if (!files.contains(item.getKey())) {
                continue;
            }
            if (update.reset) {
                rows.removeIf(entry -> entry.origin == item.getKey());
                rebuild = true;
            }
            appended.addAll(update.added);
            if (update.extended != null && !rebuild) {
                model.update(update.extended);
            }
        }
        if (appended.isEmpty() && !rebuild) {
            showDetail();
            return;
        }
        Collections.sort(appended, TIME_ORDER);
        boolean newestLast = !rows.isEmpty() && !appended.isEmpty()
                && TIME_ORDER.compare(rows.get(rows.size() - 1), appended.get(0)) <= 0;
        if (!rebuild && newestLast) {
            // The common case when following: new entries come after everything shown, so just append them.
            for (LogEntry entry : appended) {
                model.add(entry);
            }
        } else {
            LogEntry selected = selectedEntry();
            rows.addAll(appended);
            Collections.sort(rows, TIME_ORDER);
            model.setRows(rows);
            reselect(selected);
        }
        if (followButton.isSelected() && atEnd && table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
        }
        showDetail();
        updateStatus();
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<Path> paths = new ArrayList<>();
            for (File file : chooser.getSelectedFiles()) {
                paths.add(file.toPath());
            }
            open(paths.toArray(new Path[0]));
        }
    }

    private boolean isOpen(Path path) {
        for (LogFile file : files) {
            if (file.getPath().toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())) {
                return true;
            }
        }
        return false;
    }

    private void formatChosen() {
        Object item = formatBox.getSelectedItem();
        if (item == lastFormatItem) {
            return;
        }
        if (item == CUSTOM_FORMAT) {
            String current = chosenFormat != null ? chosenFormat.getRegex() : "";
            Object regex = JOptionPane.showInputDialog(this, text("logviewer.customPrompt"),
                    text("logviewer.format.custom"), JOptionPane.QUESTION_MESSAGE, null, null, current);
            if (regex == null) {
                formatBox.setSelectedItem(lastFormatItem);
                return;
            }
            try {
                setFormat(LogFormat.custom(text("logviewer.format.custom"), regex.toString()));
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, text("logviewer.invalidRegex", e.getMessage()),
                        text("logviewer.format.custom"), JOptionPane.ERROR_MESSAGE);
                formatBox.setSelectedItem(lastFormatItem);
                return;
            }
        } else {
            setFormat(item instanceof LogFormat ? (LogFormat) item : null);
        }
        lastFormatItem = item;
    }

    // ---- Filtering and display -----------------------------------------------------------------------

    private void searchChanged() {
        if (settingSearch) {
            return;
        }
        String text = search.getText();
        Pattern pattern = null;
        String error = null;
        if (!text.isEmpty()) {
            int flags = caseBox.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            try {
                pattern = Pattern.compile(regexBox.isSelected() ? text : Pattern.quote(text), flags);
            } catch (PatternSyntaxException e) {
                error = text("logviewer.invalidRegex", e.getDescription());
            }
        }
        search.putClientProperty(FlatClientProperties.OUTLINE, error != null ? FlatClientProperties.OUTLINE_ERROR : null);
        search.setToolTipText(error);
        if (error == null) {
            searchPattern = pattern;
            sorter.allRowsChanged();
            showDetail();
        }
    }

    private boolean matchesSearch(LogEntry entry) {
        return searchPattern == null
                || searchPattern.matcher(entry.getMessage()).find()
                || searchPattern.matcher(entry.getLogger()).find()
                || searchPattern.matcher(entry.getThread()).find();
    }

    private void showDetail() {
        LogEntry entry = selectedEntry();
        if (entry == null) {
            detail.setText("");
            return;
        }
        String header = entry.getTimestamp() + "  " + entry.getLevel() + "  [" + entry.getThread() + "]  "
                + entry.getLogger() + "  (" + entry.getSource() + ":" + entry.getLineNumber() + ")\n\n";
        String message = entry.getMessage();
        detail.setText(header + message);
        detail.setCaretPosition(0);
        detail.getHighlighter().removeAllHighlights();
        if (searchPattern != null) {
            Matcher matcher = searchPattern.matcher(message);
            DefaultHighlighter.DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT);
            while (matcher.find() && matcher.end() > matcher.start()) {
                try {
                    detail.getHighlighter().addHighlight(header.length() + matcher.start(),
                            header.length() + matcher.end(), painter);
                } catch (BadLocationException e) {
                    break;
                }
            }
        }
    }

    private void updateStatus() {
        if (loadError != null) {
            status.setText(loadError);
            return;
        }
        int errors = 0;
        int warnings = 0;
        for (LogEntry entry : model.getRows()) {
            if (entry.getLevel() == LogLevel.ERROR || entry.getLevel() == LogLevel.FATAL) {
                errors++;
            } else if (entry.getLevel() == LogLevel.WARN) {
                warnings++;
            }
        }
        status.setText(text("logviewer.status", table.getRowCount(), model.getRowCount(), files.size(), errors,
                warnings));
    }

    private LogEntry selectedEntry() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getRow(table.convertRowIndexToModel(row));
    }

    private void reselect(LogEntry entry) {
        int modelIndex = entry == null ? -1 : model.indexOf(entry);
        int viewIndex = modelIndex < 0 ? -1 : table.convertRowIndexToView(modelIndex);
        if (viewIndex >= 0) {
            table.setRowSelectionInterval(viewIndex, viewIndex);
        }
    }

    private boolean isScrolledToEnd() {
        Rectangle visible = table.getVisibleRect();
        return table.getRowCount() == 0 || visible.y + visible.height >= table.getHeight() - table.getRowHeight();
    }

    private String text(String key, Object... args) {
        return i18n.get(key, args);
    }

    private static <T extends Component> T fixedWidth(T component) {
        component.setMaximumSize(component.getPreferredSize());
        return component;
    }

    private final class EntryFilter extends RowFilter<BeanTableModel<LogEntry>, Integer> {
        @Override
        public boolean include(Entry<? extends BeanTableModel<LogEntry>, ? extends Integer> row) {
            LogEntry entry = model.getRow(row.getIdentifier());
            return entry.getLevel().isAtLeast(minimumLevel) && matchesSearch(entry);
        }
    }

    /** Shows "Auto-detect", "All" and "Custom regex..." for the marker items, names for the rest. */
    private final class LabelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Object label = value == AUTO_FORMAT ? text("logviewer.format.auto")
                    : value == CUSTOM_FORMAT ? text("logviewer.format.custom")
                    : value == ALL_LEVELS ? text("logviewer.level.all") : value;
            return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
        }
    }

    /** Errors in red and bold, warnings in orange. */
    private static final class LevelRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            LogLevel level = (LogLevel) value;
            super.getTableCellRendererComponent(table, level == LogLevel.UNKNOWN ? "" : level, isSelected,
                    hasFocus, row, column);
            setFont(getFont().deriveFont(level == LogLevel.ERROR || level == LogLevel.FATAL ? Font.BOLD : Font.PLAIN));
            if (!isSelected) {
                Color error = UIManager.getColor("Component.error.focusedBorderColor");
                if (level == LogLevel.ERROR || level == LogLevel.FATAL) {
                    setForeground(error != null ? error : new Color(0xD32F2F));
                } else if (level == LogLevel.WARN) {
                    setForeground(new Color(0xE08A00));
                } else {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }

    /** Paints a hint over the empty table. */
    private final class EmptyMessageTable extends JTable {
        EmptyMessageTable(BeanTableModel<LogEntry> model) {
            super(model);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (model.getRowCount() == 0) {
                String hint = text("logviewer.empty");
                g.setColor(UIManager.getColor("Label.disabledForeground"));
                FontMetrics metrics = g.getFontMetrics();
                Rectangle area = getVisibleRect();
                g.drawString(hint, area.x + (area.width - metrics.stringWidth(hint)) / 2,
                        area.y + 24 + metrics.getAscent());
            }
        }
    }

    /** Opens files dropped on the viewer. */
    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = new ArrayList<>();
                for (File file : dropped) {
                    if (file.isFile()) {
                        paths.add(file.toPath());
                    }
                }
                open(paths.toArray(new Path[0]));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
