package com.rapidswing.ui;

import javax.swing.JComponent;

/**
 * A screen shown in the main window's content area and managed by {@link ViewManager}.
 * Extend {@link AbstractView} for the common case of a panel-based view.
 */
public interface View {

    JComponent getComponent();

    default String getTitle() {
        return getClass().getSimpleName();
    }

    /** Called every time the view becomes visible. */
    default void onShow() {
    }

    /** Called every time the view is replaced by another one. */
    default void onHide() {
    }

    /** Return false to block navigation away from this view, e.g. when there are unsaved changes. */
    default boolean canLeave() {
        return true;
    }
}
