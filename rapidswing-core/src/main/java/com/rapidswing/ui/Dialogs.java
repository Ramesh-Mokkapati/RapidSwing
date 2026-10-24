package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.util.Edt;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One-line standard dialogs, all centered on the application's main window.
 * <p>
 * {@code info}, {@code warn} and {@code error} can be called from any thread.
 * {@code confirm} and {@code input} return a value, so they must be called on the EDT.
 */
public class Dialogs {

    private final Supplier<? extends Component> owner;

    public Dialogs(Supplier<? extends Component> owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public void info(String message) {
        info(text("dialog.information"), message);
    }

    public void info(String title, String message) {
        show(title, message, JOptionPane.INFORMATION_MESSAGE);
    }

    public void warn(String message) {
        show(text("dialog.warning"), message, JOptionPane.WARNING_MESSAGE);
    }

    public void error(String message) {
        show(text("dialog.error"), message, JOptionPane.ERROR_MESSAGE);
    }

    /** Shows a message for the error from {@link ErrorMessages}, with an expandable stack trace. */
    public void error(Throwable error) {
        error(text("dialog.error"), error);
    }

    /** Shows the error message with an expandable stack trace. */
    public void error(String message, Throwable error) {
        Edt.run(() -> JOptionPane.showMessageDialog(owner.get(), errorPanel(message, error), text("dialog.error"),
                JOptionPane.ERROR_MESSAGE));
    }

    public boolean confirm(String message) {
        return confirm(text("dialog.confirm"), message);
    }

    public boolean confirm(String title, String message) {
        Edt.checkEdt();
        return JOptionPane.showConfirmDialog(owner.get(), message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Returns the entered text, or empty if the user cancelled. */
    public Optional<String> input(String message, String initialValue) {
        Edt.checkEdt();
        Object value = JOptionPane.showInputDialog(owner.get(), message, text("dialog.input"),
                JOptionPane.QUESTION_MESSAGE, null, null, initialValue);
        return Optional.ofNullable((String) value);
    }

    private void show(String title, String message, int type) {
        Edt.run(() -> JOptionPane.showMessageDialog(owner.get(), message, title, type));
    }

    private static JPanel errorPanel(String message, Throwable error) {
        String summary = ErrorMessages.messageFor(error);

        JTextArea trace = new JTextArea(stackTrace(error), 12, 70);
        trace.setEditable(false);
        trace.setCaretPosition(0);
        JScrollPane details = new JScrollPane(trace);
        details.setVisible(false);

        JCheckBox toggle = new JCheckBox(text("dialog.showDetails"));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        toggle.addActionListener(e -> {
            details.setVisible(toggle.isSelected());
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) {
                window.pack();
            }
        });

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.add(new JLabel(message), BorderLayout.NORTH);
        header.add(new JLabel(summary), BorderLayout.CENTER);
        header.add(toggle, BorderLayout.SOUTH);

        panel.add(header, BorderLayout.NORTH);
        panel.add(details, BorderLayout.CENTER);
        return panel;
    }

    private static String text(String key) {
        return I18n.global().get(key);
    }

    private static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
