package com.rapidswing.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Base class for views that are themselves a panel. Uses a {@link BorderLayout} by default.
 */
public abstract class AbstractView extends JPanel implements View {

    private final String title;

    protected AbstractView(String title) {
        super(new BorderLayout());
        this.title = title;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }
}
