package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

/**
 * A multiple-document workspace (MDI): documents open as windows inside the main window, like MFC's MDI
 * applications.
 * <pre>
 * MdiWorkspace workspace = new MdiWorkspace();
 * frame.getContentPane().add(workspace);                      // or show it as a view
 * frame.getJMenuBar().add(workspace.windowMenu());            // Cascade, Tile, Close all, open windows
 * workspace.open(new DocumentView("Untitled 1"));
 * </pre>
 * Documents opened as {@link View}s are asked {@link View#canLeave()} before they close, so unsaved
 * changes can be protected.
 */
public class MdiWorkspace extends JPanel {

    private static final int CASCADE_STEP = 28;

    private final JDesktopPane desktop = new JDesktopPane();
    private final List<JInternalFrame> documents = new ArrayList<>();
    private final Property<JInternalFrame> activeDocument = new Property<>();
    private int nextCascade;

    public MdiWorkspace() {
        super(new BorderLayout());
        add(desktop, BorderLayout.CENTER);
    }

    /** Opens a view as a document window. Closing the window asks the view {@link View#canLeave()}. */
    public JInternalFrame open(View view) {
        JInternalFrame frame = open(view.getTitle(), view.getComponent());
        frame.putClientProperty(View.class, view);
        view.onShow();
        return frame;
    }

    /** Opens any component as a document window. */
    public JInternalFrame open(String title, JComponent content) {
        Edt.checkEdt();
        JInternalFrame frame = new JInternalFrame(title, true, true, true, true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(content);
        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                close(frame);
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                activeDocument.set(frame);
            }
        });
        frame.setSize(documentSize(content));
        frame.setLocation(nextCascade * CASCADE_STEP, nextCascade * CASCADE_STEP);
        nextCascade = (nextCascade + 1) % 8;
        desktop.add(frame);
        documents.add(frame);
        frame.setVisible(true);
        activate(frame);
        return frame;
    }

    /** Closes the document unless its view refuses. Returns whether it was closed. */
    public boolean close(JInternalFrame frame) {
        View view = (View) frame.getClientProperty(View.class);
        if (view != null && !view.canLeave()) {
            return false;
        }
        if (view != null) {
            view.onHide();
        }
        documents.remove(frame);
        frame.dispose();
        desktop.remove(frame);
        desktop.repaint();
        activeDocument.set(documents.isEmpty() ? null : documents.get(documents.size() - 1));
        if (activeDocument.get() != null) {
            activate(activeDocument.get());
        }
        return true;
    }

    /** Closes all documents; stops at the first one that refuses. Returns whether all were closed. */
    public boolean closeAll() {
        for (JInternalFrame frame : new ArrayList<>(documents)) {
            if (!close(frame)) {
                return false;
            }
        }
        return true;
    }

    /** Arranges the documents diagonally, each slightly offset from the previous one. */
    public void cascade() {
        int index = 0;
        for (JInternalFrame frame : documents) {
            restore(frame);
            Dimension size = cascadeSize();
            frame.setBounds(index * CASCADE_STEP, index * CASCADE_STEP, size.width, size.height);
            frame.toFront();
            index++;
        }
        nextCascade = index % 8;
    }

    /** Arranges the documents side by side in a grid that fills the workspace. */
    public void tile() {
        int count = documents.size();
        if (count == 0) {
            return;
        }
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);
        int width = Math.max(1, desktop.getWidth() / columns);
        int height = Math.max(1, desktop.getHeight() / rows);
        for (int i = 0; i < count; i++) {
            JInternalFrame frame = documents.get(i);
            restore(frame);
            frame.setBounds((i % columns) * width, (i / columns) * height, width, height);
        }
    }

    public void activate(JInternalFrame frame) {
        try {
            if (frame.isIcon()) {
                frame.setIcon(false);
            }
            frame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // A document may refuse activation; leave it as is.
        }
        frame.toFront();
        activeDocument.set(frame);
    }

    public List<JInternalFrame> documents() {
        return new ArrayList<>(documents);
    }

    /** The document window with focus, or {@code null}. */
    public Property<JInternalFrame> activeDocument() {
        return activeDocument;
    }

    public JDesktopPane getDesktop() {
        return desktop;
    }

    /**
     * The standard Window menu: Cascade, Tile, Close all and the list of open documents. The list is rebuilt
     * every time the menu opens.
     */
    public JMenu windowMenu() {
        I18n i18n = I18n.global();
        JMenu menu = new JMenu(i18n.get("mdi.window"));
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                fillWindowMenu(menu);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        fillWindowMenu(menu);
        return menu;
    }

    void fillWindowMenu(JMenu menu) {
        I18n i18n = I18n.global();
        menu.removeAll();
        menu.add(AppAction.of(i18n.get("mdi.cascade"), this::cascade)).setEnabled(!documents.isEmpty());
        menu.add(AppAction.of(i18n.get("mdi.tile"), this::tile)).setEnabled(!documents.isEmpty());
        menu.add(AppAction.of(i18n.get("mdi.closeAll"), this::closeAll)).setEnabled(!documents.isEmpty());
        menu.addSeparator();
        if (documents.isEmpty()) {
            JMenuItem none = new JMenuItem(i18n.get("mdi.noWindows"));
            none.setEnabled(false);
            menu.add(none);
            return;
        }
        ButtonGroup group = new ButtonGroup();
        int number = 1;
        for (JInternalFrame frame : documents) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(number + " " + frame.getTitle(),
                    frame == activeDocument.get());
            if (number <= 9) {
                item.setMnemonic('0' + number);
            }
            item.addActionListener(event -> activate(frame));
            group.add(item);
            menu.add(item);
            number++;
        }
    }

    private Dimension documentSize(JComponent content) {
        Dimension preferred = content.getPreferredSize();
        Dimension available = desktop.getSize();
        if (available.width == 0 || available.height == 0) {
            return new Dimension(Math.max(400, preferred.width + 20), Math.max(300, preferred.height + 40));
        }
        return new Dimension(Math.min(Math.max(preferred.width + 20, available.width * 3 / 5), available.width),
                Math.min(Math.max(preferred.height + 40, available.height * 3 / 5), available.height));
    }

    private Dimension cascadeSize() {
        Dimension available = desktop.getSize();
        if (available.width == 0 || available.height == 0) {
            return new Dimension(400, 300);
        }
        return new Dimension(available.width * 3 / 5, available.height * 3 / 5);
    }

    private static void restore(JInternalFrame frame) {
        try {
            if (frame.isIcon()) {
                frame.setIcon(false);
            }
            if (frame.isMaximum()) {
                frame.setMaximum(false);
            }
        } catch (PropertyVetoException ignored) {
            // Keep the frame's state if it refuses.
        }
    }
}
