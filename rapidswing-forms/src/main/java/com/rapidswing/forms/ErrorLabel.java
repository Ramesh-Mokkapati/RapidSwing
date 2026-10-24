package com.rapidswing.forms;

import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/** The small message under a field: red for an error, amber for a warning. Follows theme changes. */
final class ErrorLabel extends JLabel {

    private static final Color FALLBACK = new Color(0xCC3333);
    private static final Color WARNING_FALLBACK = new Color(0xB36B00);

    private boolean warning;

    ErrorLabel() {
        setVisible(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateColor();
        Font base = UIManager.getFont("Label.font");
        if (base != null) {
            setFont(base.deriveFont(base.getSize2D() - 1f));
        }
    }

    void setWarning(boolean isWarning) {
        if (warning != isWarning) {
            warning = isWarning;
            updateColor();
        }
    }

    boolean isWarning() {
        return warning;
    }

    private void updateColor() {
        Color color = UIManager.getColor(warning ? "Component.warning.focusedBorderColor"
                : "Component.error.focusedBorderColor");
        setForeground(color != null ? color : warning ? WARNING_FALLBACK : FALLBACK);
    }
}
