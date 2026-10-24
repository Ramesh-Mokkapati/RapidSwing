package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.util.Edt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the application's keyboard shortcuts: keys bound with a description on the
 * {@link MainFrame}, then the accelerators of the menu items.
 */
public final class ShortcutsDialog {

    private ShortcutsDialog() {
    }

    public static void show(MainFrame frame) {
        Edt.checkEdt();
        I18n i18n = I18n.global();
        DefaultTableModel model = new DefaultTableModel(new Object[]{i18n.get("shortcuts.key"),
                i18n.get("shortcuts.action")}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Shortcut shortcut : collect(frame)) {
            model.addRow(new Object[]{shortcut.getKeyText(), shortcut.getDescription()});
        }
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(320);
        table.setFillsViewportHeight(true);

        JDialog dialog = new JDialog(frame, i18n.get("shortcuts.title"), JDialog.ModalityType.APPLICATION_MODAL);
        JButton close = new JButton(i18n.get("sysinfo.close"));
        close.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new java.awt.Dimension(480, 320));
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /** Window-wide keys first, then menu accelerators of visible items; a key is listed once. */
    public static List<Shortcut> collect(MainFrame frame) {
        Map<KeyStroke, Shortcut> found = new LinkedHashMap<>();
        for (Shortcut shortcut : frame.shortcuts()) {
            found.putIfAbsent(shortcut.getKeyStroke(), shortcut);
        }
        JMenuBar menuBar = frame.getJMenuBar();
        if (menuBar != null) {
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu != null && menu.isVisible()) {
                    collect(menu, menu.getText(), found);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void collect(JMenu menu, String path, Map<KeyStroke, Shortcut> found) {
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenu) {
                JMenu subMenu = (JMenu) child;
                if (subMenu.isVisible()) {
                    collect(subMenu, path + " > " + subMenu.getText(), found);
                }
            } else if (child instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) child;
                if (item.isVisible() && item.getAccelerator() != null) {
                    found.putIfAbsent(item.getAccelerator(),
                            new Shortcut(item.getAccelerator(), path + " > " + item.getText()));
                }
            }
        }
    }
}
