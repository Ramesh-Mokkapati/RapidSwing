package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.util.Edt;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Opens a view in a window of its own, next to the main window, e.g. to watch one list
 * while working in another. The view is a new instance, so its state is independent of the main window's.
 */
public final class DetachedWindow {

    private DetachedWindow() {
    }

    /**
     * Opens a fresh instance of the view. Returns {@code null} if access is denied.
     *
     * @throws IllegalStateException if the view's factory always returns the same instance, which cannot be shown
     *                               twice
     */
    public static JFrame open(ViewManager views, String viewId, Window owner) {
        Edt.checkEdt();
        View view = views.create(viewId);
        if (view == null) {
            return null;
        }
        if (view.getComponent().getParent() != null) {
            throw new IllegalStateException(I18n.global().get("window.sharedView"));
        }
        JFrame frame = new JFrame(view.getTitle());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(view.getComponent());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (view.canLeave()) {
                    view.onHide();
                    frame.dispose();
                }
            }
        });
        if (owner != null) {
            frame.setIconImages(owner.getIconImages());
            frame.setSize(Math.max(600, owner.getWidth() * 3 / 4), Math.max(400, owner.getHeight() * 3 / 4));
            frame.setLocation(owner.getX() + 40, owner.getY() + 40);
        } else {
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);
        }
        view.onShow();
        frame.setVisible(true);
        return frame;
    }
}
