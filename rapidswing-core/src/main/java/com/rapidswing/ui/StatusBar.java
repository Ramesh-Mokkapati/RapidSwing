package com.rapidswing.ui;

import com.rapidswing.property.Property;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/**
 * Status bar with a message on the left and a busy indicator on the right.
 */
public class StatusBar extends JPanel {

    private final JLabel message = new JLabel(" ");
    private final JProgressBar busy = new JProgressBar();

    public StatusBar() {
        super(new BorderLayout(8, 0));
        busy.setIndeterminate(true);
        busy.setVisible(false);
        busy.setPreferredSize(new Dimension(120, 12));
        add(message, BorderLayout.CENTER);
        add(busy, BorderLayout.EAST);
    }

    public void setMessage(String text) {
        message.setText(text == null || text.isEmpty() ? " " : text);
    }

    public String getMessage() {
        return message.getText().trim();
    }

    public void setBusy(boolean isBusy) {
        busy.setVisible(isBusy);
    }

    /** Shows the busy indicator while the count is above zero, e.g. {@code TaskService.runningCount()}. */
    public void bindBusy(Property<Integer> runningCount) {
        setBusy(runningCount.get() > 0);
        runningCount.onChange(count -> setBusy(count > 0));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Re-create the border on theme changes so the separator color follows the Look and Feel.
        Color line = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line != null ? line : Color.GRAY),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
    }
}
