package com.rapidswing.crud;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.app.AppContext;
import com.rapidswing.data.BeanProperties;
import com.rapidswing.data.Filter;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;
import com.rapidswing.event.EventBus;
import com.rapidswing.forms.Form;
import com.rapidswing.forms.FormBuilder;
import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.table.BeanTableModel;
import com.rapidswing.table.DataGrid;
import com.rapidswing.table.PagingBar;
import com.rapidswing.table.SearchPanel;
import com.rapidswing.table.TableBuilder;
import com.rapidswing.task.TaskService;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;
import com.rapidswing.util.Edt;
import com.rapidswing.util.Subscription;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A complete list-and-edit screen for one type of item: a {@link DataGrid} on the left, a {@link Form}
 * on the right, with New, Delete, Refresh, Save and Cancel.
 * <pre>
 * CrudScreen&lt;Customer&gt; screen = CrudScreen.builder(Customer.class)
 *         .context(ctx)
 *         .names("Customer", "Customers")
 *         .repository(customerRepository)
 *         .newItem(Customer::new)
 *         .copy(Customer::new)                       // copy constructor
 *         .itemName(Customer::getName)
 *         .columns(table -&gt; table
 *                 .column("Name", String.class, Customer::getName)
 *                 .column("Email", String.class, Customer::getEmail))
 *         .form((form, edit) -&gt; form
 *                 .text("Name", edit.property(Customer::getName, Customer::setName)).required()
 *                 .text("Email", edit.property(Customer::getEmail, Customer::setEmail)).validate(Validators.email()))
 *         .build();
 * </pre>
 * Editing works on a copy, so Cancel discards changes and the list only changes after a successful save.
 * Repository calls run in the background. Unsaved changes are protected: selecting another row, refreshing
 * or leaving the view asks before discarding them. With {@code allowCreate/allowEdit/allowDelete(false)},
 * e.g. from {@code session.hasAnyRole(...)}, the matching buttons disappear and existing items are shown
 * read-only.
 * <p>
 * Larger screens add a {@link Builder#search search panel} with operators,
 * {@link Builder#pageSize paging}, {@link Builder#details detail tables} of the edited item (e.g. a customer's
 * orders) and automatic refresh ({@link Builder#refreshEvery}, {@link Builder#refreshOn}). With a
 * {@link QueryRepository}, searching, sorting and paging happen in the repository; with a plain
 * {@link CrudRepository}, all items are loaded and searched in memory.
 */
public class CrudScreen<T> extends AbstractView {

    private final Builder<T> config;
    private final BeanTableModel<T> model;
    private final DataGrid<T> grid;
    private final TaskService tasks;
    private final Predicate<String> confirm;
    private final Consumer<String> status;
    private final BiConsumer<String, Throwable> errors;

    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorArea = new JPanel(editorCards);
    private final JLabel header = new JLabel();
    private final JLabel placeholder = new JLabel("", SwingConstants.CENTER);
    private final JPanel formHolder = new WidthTrackingPanel();
    private final JPanel editorButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

    private final AppAction newAction;
    private final AppAction deleteAction;
    private final AppAction refreshAction;
    private final AppAction saveAction;
    private final AppAction cancelAction;

    private final Property<Boolean> dirty = new Property<>(false);
    private final Property<Boolean> busy = new Property<>(false);

    private Form form;
    private T original;
    private T current;
    private boolean editingNew;
    private boolean syncingSelection;
    private boolean loaded;
    private Subscription localeSubscription;

    private final SearchPanel searchPanel;
    private final PagingBar pagingBar;
    private final List<Detail<T, ?>> details = new ArrayList<>();
    private final JComponent detailsArea;
    private final Timer refreshTimer;
    private final List<Subscription> eventSubscriptions = new ArrayList<>();
    private List<Filter> filters = Collections.emptyList();
    private int detailGeneration;
    private boolean shown;

    private CrudScreen(Builder<T> config) {
        super(config.plural);
        this.config = config;
        this.tasks = config.tasks != null ? config.tasks : new TaskService();
        this.confirm = config.confirm != null ? config.confirm : message ->
                JOptionPane.showConfirmDialog(this, message, text("dialog.confirm"), JOptionPane.YES_NO_OPTION)
                        == JOptionPane.YES_OPTION;
        this.status = config.status != null ? config.status : message -> { };
        this.errors = config.errors != null ? config.errors : (message, error) ->
                JOptionPane.showMessageDialog(this, message + "\n" + error.getMessage(), text("dialog.error"),
                        JOptionPane.ERROR_MESSAGE);

        model = config.columns.apply(BeanTableModel.builder(config.type)).build();
        newAction = AppAction.of("New", this::newItem).shortcut("ctrl N");
        deleteAction = AppAction.of("Delete", this::deleteCurrent);
        refreshAction = AppAction.of("Refresh", this::refresh).shortcut("F5");
        saveAction = AppAction.of("Save", this::save).shortcut("ctrl S");
        cancelAction = AppAction.of("Cancel", this::cancel);
        // Key bindings are registered by these English ids; the visible names are translated in updateTexts().

        grid = new DataGrid<>(model);
        if (config.allowCreate) {
            grid.addAction(newAction);
        }
        if (config.allowDelete) {
            grid.addAction(deleteAction);
        }
        grid.addAction(refreshAction);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));

        pagingBar = config.pageSize > 0 ? new PagingBar(config.pageSize) : null;
        if (pagingBar != null) {
            pagingBar.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 4));
            pagingBar.onChange(this::reloadPage);
            grid.onSortChanged(this::reloadPage);
        }
        searchPanel = config.search == null ? null
                : config.search.apply(new SearchPanel("crud." + config.type.getName()));
        if (searchPanel != null) {
            searchPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 4));
            searchPanel.onSearch(found -> {
                if (busy.get() || !confirmDiscard()) {
                    return;
                }
                filters = new ArrayList<>(found);
                if (pagingBar != null) {
                    pagingBar.reset();
                }
                load();
            });
        }
        for (DetailConfig<T, ?> detailConfig : config.details) {
            details.add(detailConfig.create(this));
        }
        detailsArea = buildDetailsArea();
        refreshTimer = config.refreshSeconds > 0 ? new Timer(config.refreshSeconds * 1000, e -> autoRefresh()) : null;
        grid.selectedItem().onChange(item -> {
            if (!syncingSelection) {
                selectionChanged(item);
            }
        });

        buildEditorArea();
        JPanel listArea = new JPanel(new BorderLayout());
        if (searchPanel != null) {
            listArea.add(searchPanel, BorderLayout.NORTH);
        }
        listArea.add(grid, BorderLayout.CENTER);
        if (pagingBar != null) {
            listArea.add(pagingBar, BorderLayout.SOUTH);
        }
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listArea, editorArea);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        bindKey(newAction, "ctrl N");
        bindKey(saveAction, "ctrl S");
        bindKey(refreshAction, "F5");
        dirty.onChange(value -> updateState());
        busy.onChange(value -> updateState());
        updateTexts();
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    // ---- Operations (also usable from code and tests) ------------------------------------------------

    /** Reloads the items (the current search and page). Asks first if there are unsaved changes. */
    public void refresh() {
        if (busy.get() || !confirmDiscard()) {
            return;
        }
        load();
    }

    /** The query for the current search, sort order and page. */
    public Query currentQuery() {
        Query query = Query.all().withFilters(filters).withSorts(grid.sortOrder());
        return pagingBar != null ? pagingBar.apply(query) : query;
    }

    public SearchPanel getSearchPanel() {
        return searchPanel;
    }

    public PagingBar getPagingBar() {
        return pagingBar;
    }

    /** The grid of a {@link Builder#details detail table}, in the order they were added. */
    @SuppressWarnings("unchecked")
    public <D> DataGrid<D> getDetailGrid(int index) {
        return (DataGrid<D>) details.get(index).grid;
    }

    private void reloadPage() {
        if (!busy.get() && confirmDiscard()) {
            load();
        }
    }

    /** Refreshes from the timer or an event, but never over unsaved changes or a running call. */
    private void autoRefresh() {
        if (shown && !busy.get() && !dirty.get()) {
            load();
        }
    }

    private void load() {
        busy.set(true);
        grid.setLoading(true);
        Query query = currentQuery();
        CrudRepository<T> repository = config.repository;
        boolean queried = searchPanel != null || pagingBar != null;
        tasks.call(() -> {
            if (repository instanceof QueryRepository) {
                return ((QueryRepository<T>) repository).find(queried ? query : Query.all());
            }
            List<T> all = repository.findAll();
            return queried ? query.apply(all, BeanProperties::get) : Page.of(all);
        })
                .onSuccess(this::loaded)
                .onError(e -> errors.accept(text("crud.loadFailed"), e))
                .onFinally(() -> {
                    grid.setLoading(false);
                    busy.set(false);
                })
                .start();
    }

    /** Starts editing a new item. Asks first if there are unsaved changes. */
    public void newItem() {
        if (!config.allowCreate || busy.get() || !confirmDiscard()) {
            return;
        }
        syncingSelection = true;
        try {
            grid.getTable().clearSelection();
        } finally {
            syncingSelection = false;
        }
        startEditing(config.newItem.get(), true);
    }

    /** Validates the form and saves the edited item in the background. */
    public void save() {
        if (current == null || busy.get() || !canSaveCurrent()) {
            return;
        }
        if (!form.validate()) {
            status.accept(text("crud.fixFields"));
            return;
        }
        T toSave = current;
        T replaced = original;
        boolean wasNew = editingNew;
        busy.set(true);
        tasks.call(() -> config.repository.save(toSave))
                .onSuccess(saved -> {
                    T result = saved != null ? saved : toSave;
                    if (wasNew) {
                        model.add(result);
                    } else {
                        model.replace(replaced, result);
                    }
                    selectQuietly(result);
                    startEditing(result, false);
                    status.accept(text("crud.saved", config.itemName.apply(result)));
                })
                .onError(e -> errors.accept(text("crud.saveFailed", displayName()), e))
                .onFinally(() -> busy.set(false))
                .start();
    }

    /** Discards the edits: a new item is dropped, an existing one shows its saved values again. */
    public void cancel() {
        if (current == null || busy.get()) {
            return;
        }
        if (editingNew) {
            clearEditor();
        } else {
            startEditing(original, false);
        }
    }

    /** Deletes the item being edited, after confirmation. */
    public void deleteCurrent() {
        T item = original;
        if (!config.allowDelete || item == null || editingNew || busy.get()) {
            return;
        }
        if (!confirm.test(text("crud.confirmDelete", config.itemName.apply(item)))) {
            return;
        }
        busy.set(true);
        tasks.call(() -> {
            config.repository.delete(item);
            return null;
        }).onSuccess(ignored -> {
            model.remove(item);
            clearEditor();
            status.accept(text("crud.deleted", config.itemName.apply(item)));
        }).onError(e -> errors.accept(text("crud.deleteFailed", config.itemName.apply(item)), e))
                .onFinally(() -> busy.set(false))
                .start();
    }

    // ---- State ---------------------------------------------------------------------------------------

    /** True while the edited item has changes that are not saved. */
    public Property<Boolean> dirty() {
        return dirty;
    }

    /** True while a repository call is running. */
    public Property<Boolean> busy() {
        return busy;
    }

    /** The working copy being edited, or {@code null}. */
    public T getEditedItem() {
        return current;
    }

    public boolean isEditingNew() {
        return editingNew;
    }

    /** The current editor form, or {@code null} when nothing is being edited. */
    public Form getForm() {
        return form;
    }

    public DataGrid<T> getGrid() {
        return grid;
    }

    // ---- View lifecycle ------------------------------------------------------------------------------

    @Override
    public void onShow() {
        shown = true;
        if (!loaded) {
            refresh();
        }
        if (refreshTimer != null) {
            refreshTimer.start();
        }
        for (Class<?> eventType : config.refreshEvents) {
            eventSubscriptions.add(config.events.subscribeOnEdt(eventType, event -> autoRefresh()));
        }
    }

    @Override
    public void onHide() {
        shown = false;
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        eventSubscriptions.forEach(Subscription::unsubscribe);
        eventSubscriptions.clear();
    }

    @Override
    public boolean canLeave() {
        return confirmDiscard();
    }

    // ---- Internals -----------------------------------------------------------------------------------

    private void loaded(Page<T> page) {
        List<T> rows = page.getItems();
        if (pagingBar != null) {
            pagingBar.show(page);
        }
        loaded = true;
        syncingSelection = true;
        try {
            grid.setRows(rows);
        } finally {
            syncingSelection = false;
        }
        int index = original == null ? -1 : rows.indexOf(original);
        if (index >= 0) {
            startEditing(rows.get(index), false);
        } else {
            clearEditor();
        }
        status.accept(text("crud.loaded", page.getTotal()));
    }

    private void selectionChanged(T item) {
        if (item == null || (!editingNew && item.equals(original))) {
            return;
        }
        if (!confirmDiscard()) {
            // Put the selection back on the row being edited.
            if (editingNew || original == null) {
                grid.getTable().clearSelection();
            } else {
                selectQuietly(original);
            }
            return;
        }
        startEditing(item, false);
    }

    private void startEditing(T item, boolean isNew) {
        Edt.checkEdt();
        if (form != null) {
            form.unbind();
        }
        editingNew = isNew;
        original = isNew ? null : item;
        current = isNew ? item : config.copy.apply(item);

        EditContext<T> edit = new EditContext<>(current, isNew, () -> dirty.set(true));
        form = config.form.apply(Form.builder(), edit).build();
        form.setReadOnly(!canSaveCurrent());
        formHolder.removeAll();
        formHolder.add(form.getComponent(), BorderLayout.NORTH);
        formHolder.revalidate();
        formHolder.repaint();

        dirty.set(false);
        editorCards.show(editorArea, "editor");
        updateState();
        loadDetails(isNew ? null : item);
    }

    /** Loads the detail tables of the item, ignoring results that arrive after another item was opened. */
    private void loadDetails(T parent) {
        int generation = ++detailGeneration;
        if (detailsArea != null) {
            detailsArea.setVisible(parent != null);
        }
        for (Detail<T, ?> detail : details) {
            detail.load(parent, generation);
        }
    }

    private void clearEditor() {
        if (form != null) {
            form.unbind();
            form = null;
        }
        current = null;
        original = null;
        editingNew = false;
        formHolder.removeAll();
        dirty.set(false);
        editorCards.show(editorArea, "empty");
        updateState();
        loadDetails(null);
    }

    private boolean confirmDiscard() {
        return !dirty.get() || confirm.test(text("crud.confirmDiscard", displayName()));
    }

    private void selectQuietly(T item) {
        syncingSelection = true;
        try {
            grid.select(item);
        } finally {
            syncingSelection = false;
        }
    }

    private String displayName() {
        return editingNew || original == null ? text("crud.theNewEntry") : config.itemName.apply(original);
    }

    /** Whether the user may save the item being edited: new items need create, existing ones edit permission. */
    private boolean canSaveCurrent() {
        return editingNew ? config.allowCreate : config.allowEdit;
    }

    private void updateState() {
        boolean editing = current != null;
        boolean idle = !busy.get();
        newAction.setEnabled(idle && config.allowCreate);
        refreshAction.setEnabled(idle);
        deleteAction.setEnabled(idle && editing && !editingNew && config.allowDelete);
        saveAction.setEnabled(idle && editing && canSaveCurrent() && (editingNew || dirty.get()));
        cancelAction.setEnabled(idle && editing && canSaveCurrent());
        editorButtons.setVisible(editing && canSaveCurrent());
        if (editing) {
            String title = editingNew ? text("crud.newItem", config.singular) : config.itemName.apply(original);
            header.setText(dirty.get() ? title + " *" : title);
        }
    }

    private void buildEditorArea() {
        placeholder.setEnabled(false);

        header.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        formHolder.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(formHolder);
        scroll.setBorder(null);

        editorButtons.add(new JButton(cancelAction));
        editorButtons.add(new JButton(saveAction));

        JPanel formArea = new JPanel(new BorderLayout());
        formArea.add(scroll, BorderLayout.CENTER);
        formArea.add(editorButtons, BorderLayout.SOUTH);
        JPanel editor = new JPanel(new BorderLayout());
        editor.add(header, BorderLayout.NORTH);
        if (detailsArea == null) {
            editor.add(formArea, BorderLayout.CENTER);
        } else {
            JSplitPane formAndDetails = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formArea, detailsArea);
            formAndDetails.setResizeWeight(0.6);
            formAndDetails.setBorder(null);
            editor.add(formAndDetails, BorderLayout.CENTER);
        }

        editorArea.add(placeholder, "empty");
        editorArea.add(editor, "editor");
        editorCards.show(editorArea, "empty");
    }

    /** Follows locale changes only while displayed, so discarded screens are not kept alive by I18n. */
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

    /** One detail table, or tabs if there are several. */
    private JComponent buildDetailsArea() {
        if (details.isEmpty()) {
            return null;
        }
        if (details.size() == 1) {
            Detail<T, ?> only = details.get(0);
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            JLabel title = new JLabel(only.title);
            title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
            panel.add(title, BorderLayout.NORTH);
            panel.add(only.grid, BorderLayout.CENTER);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
            return panel;
        }
        JTabbedPane tabs = new JTabbedPane();
        for (Detail<T, ?> detail : details) {
            tabs.addTab(detail.title, detail.grid);
        }
        tabs.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
        return tabs;
    }

    private void updateTexts() {
        newAction.putValue(AppAction.NAME, text("crud.new"));
        deleteAction.putValue(AppAction.NAME, text("crud.delete"));
        refreshAction.putValue(AppAction.NAME, text("crud.refresh"));
        saveAction.putValue(AppAction.NAME, text("crud.save"));
        cancelAction.putValue(AppAction.NAME, text("crud.cancel"));
        grid.emptyText(text("crud.empty"));
        placeholder.setText(text("crud.placeholder"));
        updateState();
    }

    private static String text(String key, Object... args) {
        return I18n.global().get(key, args);
    }

    private void bindKey(AppAction action, String keyStroke) {
        String key = "rapidswing.crud." + action.getValue(AppAction.NAME);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), key);
        getActionMap().put(key, action);
    }

    /** A panel that takes the width of its scroll pane, so form fields shrink instead of scrolling sideways. */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Loads the detail rows of an item, on a background thread, e.g. a customer's orders. */
    @FunctionalInterface
    public interface DetailLoader<T, D> {
        List<D> load(T parent) throws Exception;
    }

    private static final class DetailConfig<T, D> {
        final String title;
        final Class<D> type;
        final Function<TableBuilder<D>, TableBuilder<D>> columns;
        final DetailLoader<? super T, D> loader;

        DetailConfig(String title, Class<D> type, Function<TableBuilder<D>, TableBuilder<D>> columns,
                     DetailLoader<? super T, D> loader) {
            this.title = Objects.requireNonNull(title, "title");
            this.type = Objects.requireNonNull(type, "type");
            this.columns = Objects.requireNonNull(columns, "columns");
            this.loader = Objects.requireNonNull(loader, "loader");
        }

        Detail<T, D> create(CrudScreen<T> screen) {
            return new Detail<>(screen, this);
        }
    }

    /** A read-only table of rows belonging to the edited item. */
    private static final class Detail<T, D> {
        final CrudScreen<T> screen;
        final DetailConfig<T, D> config;
        final String title;
        final DataGrid<D> grid;

        Detail(CrudScreen<T> screen, DetailConfig<T, D> config) {
            this.screen = screen;
            this.config = config;
            this.title = config.title;
            this.grid = new DataGrid<>(config.columns.apply(BeanTableModel.builder(config.type)).build());
        }

        void load(T parent, int generation) {
            if (parent == null) {
                grid.setRows(Collections.<D>emptyList());
                return;
            }
            grid.setLoading(true);
            screen.tasks.call(() -> config.loader.load(parent))
                    .onSuccess(rows -> {
                        if (generation == screen.detailGeneration) {
                            grid.setRows(rows);
                        }
                    })
                    .onError(e -> screen.errors.accept(text("crud.detailsFailed", title), e))
                    .onFinally(() -> grid.setLoading(false))
                    .start();
        }
    }

    /** Configures a {@link CrudScreen}. Required: repository, newItem, copy, columns and form. */
    public static final class Builder<T> {

        private final Class<T> type;
        private String singular = "Item";
        private String plural = "Items";
        private CrudRepository<T> repository;
        private Supplier<? extends T> newItem;
        private UnaryOperator<T> copy;
        private Function<? super T, String> itemName = String::valueOf;
        private Function<TableBuilder<T>, TableBuilder<T>> columns;
        private BiFunction<FormBuilder, EditContext<T>, FormBuilder> form;
        private boolean allowCreate = true;
        private boolean allowEdit = true;
        private boolean allowDelete = true;
        private TaskService tasks;
        private Predicate<String> confirm;
        private Consumer<String> status;
        private BiConsumer<String, Throwable> errors;
        private Function<SearchPanel, SearchPanel> search;
        private int pageSize;
        private final List<DetailConfig<T, ?>> details = new ArrayList<>();
        private int refreshSeconds;
        private EventBus events;
        private final List<Class<?>> refreshEvents = new ArrayList<>();

        private Builder(Class<T> type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        /** Uses the app's task service, confirmation dialogs, status bar and error dialog. */
        public Builder<T> context(AppContext ctx) {
            this.tasks = ctx.tasks();
            this.confirm = ctx.dialogs()::confirm;
            this.status = ctx.mainFrame().statusBar()::setMessage;
            this.errors = ctx.dialogs()::error;
            return this;
        }

        /** E.g. "Customer", "Customers": the view title and the "New ..." editor title. Pass translated names. */
        public Builder<T> names(String singular, String plural) {
            this.singular = Objects.requireNonNull(singular, "singular");
            this.plural = Objects.requireNonNull(plural, "plural");
            return this;
        }

        public Builder<T> repository(CrudRepository<T> repository) {
            this.repository = repository;
            return this;
        }

        /** Creates the blank item that New starts from. */
        public Builder<T> newItem(Supplier<? extends T> factory) {
            this.newItem = factory;
            return this;
        }

        /** Creates an independent copy to edit, typically a copy constructor. */
        public Builder<T> copy(UnaryOperator<T> copier) {
            this.copy = copier;
            return this;
        }

        /** Short display name of an item, for the editor title and confirmations. */
        public Builder<T> itemName(Function<? super T, String> name) {
            this.itemName = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder<T> columns(Function<TableBuilder<T>, TableBuilder<T>> definition) {
            this.columns = definition;
            return this;
        }

        /** Builds the editor form for each edited copy. Return the builder; the screen calls {@code build()}. */
        public Builder<T> form(BiFunction<FormBuilder, EditContext<T>, FormBuilder> definition) {
            this.form = definition;
            return this;
        }

        public Builder<T> tasks(TaskService tasks) {
            this.tasks = tasks;
            return this;
        }

        /** Asks the user a yes/no question. Defaults to a dialog. */
        public Builder<T> confirmWith(Predicate<String> confirm) {
            this.confirm = confirm;
            return this;
        }

        public Builder<T> statusWith(Consumer<String> status) {
            this.status = status;
            return this;
        }

        public Builder<T> errorsWith(BiConsumer<String, Throwable> errors) {
            this.errors = errors;
            return this;
        }

        /** Whether New is offered. Typically {@code session.hasAnyRole(...)}; views are rebuilt per sign-in. */
        public Builder<T> allowCreate(boolean allowed) {
            this.allowCreate = allowed;
            return this;
        }

        /** Whether existing items can be changed; if not, they are shown read-only. */
        public Builder<T> allowEdit(boolean allowed) {
            this.allowEdit = allowed;
            return this;
        }

        public Builder<T> allowDelete(boolean allowed) {
            this.allowDelete = allowed;
            return this;
        }

        /**
         * A search panel above the list, with an operator per field; use the bean property names as fields.
         * With a {@link QueryRepository} the repository searches; otherwise the loaded items are searched.
         */
        public Builder<T> search(Function<SearchPanel, SearchPanel> definition) {
            this.search = Objects.requireNonNull(definition, "definition");
            return this;
        }

        /**
         * Loads and shows {@code rows} items per page, with a paging bar under the list. Clicking a column with a
         * {@link com.rapidswing.table.ColumnBuilder#field field} sorts all items, not just the page.
         */
        public Builder<T> pageSize(int rows) {
            if (rows <= 0) {
                throw new IllegalArgumentException("pageSize must be positive: " + rows);
            }
            this.pageSize = rows;
            return this;
        }

        /**
         * A read-only table of rows belonging to the edited item, under the form:
         * {@code details("Orders", Order.class, t -> t.column(...), customer -> orders.findFor(customer))}.
         * Several details are shown as tabs.
         */
        public <D> Builder<T> details(String title, Class<D> rowType, Function<TableBuilder<D>, TableBuilder<D>> columns,
                                      DetailLoader<? super T, D> loader) {
            details.add(new DetailConfig<>(title, rowType, columns, loader));
            return this;
        }

        /** Reloads every {@code seconds} while the screen is shown, unless there are unsaved changes. */
        public Builder<T> refreshEvery(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("seconds must be positive: " + seconds);
            }
            this.refreshSeconds = seconds;
            return this;
        }

        /**
         * Reloads when one of these events is published while the screen is shown (unless there are unsaved
         * changes), e.g. from a message queue: for example, publish a {@code CustomersChanged} event when a
         * message arrives from the server.
         */
        public Builder<T> refreshOn(EventBus bus, Class<?>... eventTypes) {
            this.events = Objects.requireNonNull(bus, "bus");
            Collections.addAll(refreshEvents, eventTypes);
            return this;
        }

        public CrudScreen<T> build() {
            Edt.checkEdt();
            require(repository, "repository");
            require(newItem, "newItem");
            require(copy, "copy");
            require(columns, "columns");
            require(form, "form");
            return new CrudScreen<>(this);
        }

        private static void require(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException("CrudScreen." + name + "(...) is required");
            }
        }
    }
}
