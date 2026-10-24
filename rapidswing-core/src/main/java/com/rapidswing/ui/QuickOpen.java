package com.rapidswing.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Go to anything": type part of a view or command name and press Enter.
 * {@link MainFrame#enableQuickOpen} binds it to Ctrl+K.
 */
public final class QuickOpen {

    private QuickOpen() {
    }

    /** Shows the search popup over the window and runs the chosen command. */
    public static void show(Window owner, Supplier<CommandIndex> index) {
        CommandIndex commands = index.get();
        JDialog dialog = new JDialog(owner, I18n.global().get("quickopen.title"), JDialog.ModalityType.APPLICATION_MODAL);
        JTextField field = new JTextField(36);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, I18n.global().get("quickopen.placeholder"));
        DefaultListModel<CommandIndex.Command> model = new DefaultListModel<>();
        JList<CommandIndex.Command> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(10);

        Runnable update = () -> {
            model.clear();
            List<CommandIndex.Command> found = commands.search(field.getText());
            found.forEach(model::addElement);
            if (!found.isEmpty()) {
                list.setSelectedIndex(0);
            }
        };
        Runnable runSelected = () -> {
            CommandIndex.Command command = list.getSelectedValue();
            dialog.dispose();
            if (command != null) {
                command.run();
            }
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update.run();
            }
        });
        field.addActionListener(e -> runSelected.run());
        bind(field, "DOWN", () -> move(list, 1));
        bind(field, "UP", () -> move(list, -1));
        bind(field, "ESCAPE", dialog::dispose);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelected.run();
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(field, BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.setContentPane(content);
        update.run();
        dialog.pack();
        if (owner != null) {
            dialog.setLocation(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2, owner.getY() + 80);
        }
        dialog.setVisible(true);
    }

    private static void move(JList<?> list, int delta) {
        int size = list.getModel().getSize();
        if (size > 0) {
            int index = Math.max(0, Math.min(size - 1, list.getSelectedIndex() + delta));
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        }
    }

    private static void bind(JComponent component, String key, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "rapidswing.quickopen." + key);
        component.getActionMap().put("rapidswing.quickopen." + key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
