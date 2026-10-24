package com.rapidswing.demo.view;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.app.AppContext;
import com.rapidswing.i18n.I18n;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;
import com.rapidswing.ui.FunctionKeyBar;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * A keyboard-only screen for a warehouse scanner: scan (type) an item and press
 * Enter, then confirm with F2. Every action has a function key, shown along the bottom.
 */
public class PickingView extends AbstractView {

    private final DefaultListModel<String> picked = new DefaultListModel<>();
    private final JTextField scan = new JTextField(20);
    private final JLabel status = new JLabel(" ");
    private final FunctionKeyBar keys;

    public PickingView(AppContext ctx) {
        super(ctx.i18n().get("picking.title"));
        I18n i18n = ctx.i18n();
        scan.setFont(scan.getFont().deriveFont(Font.BOLD, scan.getFont().getSize2D() * 1.6f));
        scan.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, i18n.get("picking.scan"));
        scan.addActionListener(e -> add());

        AppAction confirm = AppAction.of(i18n.get("picking.confirm"), () -> {
            status.setText(i18n.get("picking.confirmed", picked.size()));
            picked.clear();
        });
        AppAction undo = AppAction.of(i18n.get("picking.undo"), () -> picked.remove(picked.size() - 1));
        AppAction clear = AppAction.of(i18n.get("picking.clear"), picked::clear);
        Runnable updateKeys = () -> {
            confirm.setEnabled(!picked.isEmpty());
            undo.setEnabled(!picked.isEmpty());
            clear.setEnabled(!picked.isEmpty());
        };
        picked.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                updateKeys.run();
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                updateKeys.run();
            }

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                updateKeys.run();
            }
        });
        updateKeys.run();
        keys = new FunctionKeyBar()
                .key("F2", confirm)
                .key("F4", undo)
                .key("F8", clear)
                .key("F12", i18n.get("picking.back"), ctx.views()::back);

        JList<String> list = new JList<>(picked);
        list.setFont(list.getFont().deriveFont(list.getFont().getSize2D() * 1.3f));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        JLabel heading = new JLabel(i18n.get("picking.order"));
        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        top.add(heading, BorderLayout.NORTH);
        top.add(scan, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 24, 8, 24));
        content.add(top, BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        add(keys, BorderLayout.SOUTH);
    }

    @Override
    public void onShow() {
        scan.requestFocusInWindow();
    }

    public FunctionKeyBar getKeys() {
        return keys;
    }

    public JTextField getScanField() {
        return scan;
    }

    public int pickedCount() {
        return picked.size();
    }

    public String getStatus() {
        return status.getText().trim();
    }

    private void add() {
        String item = scan.getText().trim();
        if (!item.isEmpty()) {
            picked.addElement(item);
            status.setText(" ");
        }
        scan.setText("");
    }
}
