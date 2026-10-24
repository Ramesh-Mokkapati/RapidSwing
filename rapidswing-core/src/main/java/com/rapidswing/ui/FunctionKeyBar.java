package com.rapidswing.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A row of function-key buttons along the bottom of a screen, for terminal-style screens (warehouse scanners,
 * keyboard-only data entry): "F2 Save", "F3 Search", "F12 Back". Each key works while the window containing the
 * bar is focused, and its button shows the key and the action's name.
 * <pre>
 * FunctionKeyBar keys = new FunctionKeyBar()
 *         .key("F2", saveAction)
 *         .key("F3", searchAction)
 *         .key("F12", "Back", this::goBack);
 * view.add(keys, BorderLayout.SOUTH);
 * </pre>
 * Disabled actions disable their key. Names may be translation keys.
 */
public class FunctionKeyBar extends JPanel {

    private final Map<String, JButton> buttons = new LinkedHashMap<>();

    public FunctionKeyBar() {
        super(new GridLayout(1, 0, 6, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    /** Binds the key (e.g. {@code "F2"}, {@code "shift F5"}) to the action. */
    public FunctionKeyBar key(String keyStroke, Action action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        if (stroke == null) {
            throw new IllegalArgumentException("Invalid key stroke: '" + keyStroke + "'");
        }
        String keyText = Shortcut.keyText(stroke);
        JButton button = new JButton(action);
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        Runnable label = () -> button.setText("<html><center><b>" + keyText + "</b><br>"
                + escape(text(String.valueOf(action.getValue(Action.NAME)))) + "</center></html>");
        label.run();
        PropertyChangeListener nameListener = e -> {
            if (Action.NAME.equals(e.getPropertyName())) {
                label.run();
            }
        };
        action.addPropertyChangeListener(nameListener);
        String id = "rapidswing.fkey." + keyStroke;
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, id);
        getActionMap().put(id, action);
        JButton replaced = buttons.put(keyStroke, button);
        if (replaced != null) {
            remove(replaced);
        }
        add(button);
        revalidate();
        return this;
    }

    public FunctionKeyBar key(String keyStroke, String name, Runnable handler) {
        return key(keyStroke, new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.run();
            }
        });
    }

    /** The button of a key, e.g. for tests. */
    public JButton button(String keyStroke) {
        JButton button = buttons.get(keyStroke);
        if (button == null) {
            throw new IllegalArgumentException("No key '" + keyStroke + "'");
        }
        return button;
    }

    /** Presses the key as the user would; returns false if its action is disabled. */
    public boolean press(String keyStroke) {
        Action action = button(keyStroke).getAction();
        if (!action.isEnabled()) {
            return false;
        }
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, keyStroke));
        return true;
    }

    private static String text(String keyOrText) {
        return I18n.global().has(keyOrText) ? I18n.global().get(keyOrText) : keyOrText;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
