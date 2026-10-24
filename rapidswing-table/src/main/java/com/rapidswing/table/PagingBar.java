package com.rapidswing.table;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.data.Page;
import com.rapidswing.data.Query;
import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Subscription;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;

/**
 * Page navigation for results loaded page by page: first, previous, "Page 2 of 7", next,
 * last, the number of rows and the page size.
 * <pre>
 * PagingBar paging = new PagingBar(50);
 * paging.onChange(() -&gt; load(query.page(paging.pageIndex().get(), paging.pageSize().get())));
 * // after loading:
 * paging.show(page);
 * </pre>
 * Changing the page or page size calls the handler; {@link #show} does not. Use it on the EDT.
 */
public class PagingBar extends JPanel {

    private static final Integer[] PAGE_SIZES = {25, 50, 100, 200, 500};

    private final Property<Integer> pageIndex = new Property<>(0);
    private final Property<Integer> pageSize;
    private final JButton first = new JButton("|<");
    private final JButton previous = new JButton("<");
    private final JButton next = new JButton(">");
    private final JButton last = new JButton(">|");
    private final JLabel position = new JLabel();
    private final JLabel total = new JLabel();
    private final JLabel sizeLabel = new JLabel();
    private final JComboBox<Integer> sizes = new JComboBox<>(PAGE_SIZES);
    private Runnable onChange = () -> { };
    private int pageCount = 1;
    private long rowCount;
    private boolean updating;
    private Subscription localeSubscription;

    public PagingBar(int initialPageSize) {
        // Three regions rather than one flow, so long (e.g. German) labels never push controls onto a hidden line.
        super(new BorderLayout(12, 0));
        this.pageSize = new Property<>(initialPageSize);
        boolean listed = false;
        for (Integer size : PAGE_SIZES) {
            listed |= size == initialPageSize;
        }
        if (!listed) {
            sizes.insertItemAt(initialPageSize, 0);
        }
        sizes.setSelectedItem(initialPageSize);
        first.addActionListener(e -> goTo(0));
        previous.addActionListener(e -> goTo(pageIndex.get() - 1));
        next.addActionListener(e -> goTo(pageIndex.get() + 1));
        last.addActionListener(e -> goTo(pageCount - 1));
        sizes.addActionListener(e -> {
            if (!updating && sizes.getSelectedItem() != null && !sizes.getSelectedItem().equals(pageSize.get())) {
                pageSize.set((Integer) sizes.getSelectedItem());
                pageIndex.set(0);
                onChange.run();
            }
        });
        for (JButton button : new JButton[]{first, previous, next, last}) {
            button.setFocusable(false);
            // Narrow arrow buttons instead of the theme's wide minimum width, so the bar fits beside long labels.
            button.putClientProperty(FlatClientProperties.STYLE, "minimumWidth: 30; margin: 2,6,2,6");
        }
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        navigation.add(first);
        navigation.add(previous);
        navigation.add(position);
        navigation.add(next);
        navigation.add(last);
        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pageSizePanel.add(sizeLabel);
        pageSizePanel.add(sizes);
        add(total, BorderLayout.WEST);
        add(navigation, BorderLayout.CENTER);
        add(pageSizePanel, BorderLayout.EAST);
        updateTexts();
    }

    /** Called when the user changes the page or the page size; load the page then. */
    public PagingBar onChange(Runnable handler) {
        this.onChange = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** The current page, from 0. */
    public Property<Integer> pageIndex() {
        return pageIndex;
    }

    public Property<Integer> pageSize() {
        return pageSize;
    }

    /** The query for the current page. */
    public Query apply(Query query) {
        return query.page(pageIndex.get(), pageSize.get());
    }

    /** Shows where a loaded page is: its number, the page count and the total number of rows. */
    public void show(Page<?> page) {
        pageCount = page.getPageCount();
        rowCount = page.getTotal();
        pageIndex.set(Math.min(page.getIndex(), pageCount - 1));
        updateTexts();
    }

    /** Goes back to the first page without loading, e.g. before a new search. */
    public void reset() {
        pageIndex.set(0);
        updateTexts();
    }

    /** Shows the page if it exists and calls the change handler. Returns false if it is out of range. */
    public boolean goTo(int index) {
        if (index < 0 || index >= pageCount || index == pageIndex.get()) {
            return false;
        }
        pageIndex.set(index);
        updateTexts();
        onChange.run();
        return true;
    }

    public int getPageCount() {
        return pageCount;
    }

    /** "Page 2 of 7", as shown. */
    public String getPositionText() {
        return position.getText();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (localeSubscription == null) {
            localeSubscription = I18n.global().onChange(this::updateTexts);
        }
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
        I18n i18n = I18n.global();
        updating = true;
        try {
            position.setText(i18n.get("paging.page", pageIndex.get() + 1, pageCount));
            total.setText(i18n.get("paging.total", rowCount));
            sizeLabel.setText(i18n.get("paging.pageSize"));
            first.setToolTipText(i18n.get("paging.first"));
            previous.setToolTipText(i18n.get("paging.previous"));
            next.setToolTipText(i18n.get("paging.next"));
            last.setToolTipText(i18n.get("paging.last"));
            boolean atStart = pageIndex.get() <= 0;
            boolean atEnd = pageIndex.get() >= pageCount - 1;
            first.setEnabled(!atStart);
            previous.setEnabled(!atStart);
            next.setEnabled(!atEnd);
            last.setEnabled(!atEnd);
        } finally {
            updating = false;
        }
    }
}
