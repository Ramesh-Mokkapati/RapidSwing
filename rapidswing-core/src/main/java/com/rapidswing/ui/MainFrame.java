package com.rapidswing.ui;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The application shell: menu bar, toolbar, view area and status bar.
 * Closing the window is handled by {@link com.rapidswing.app.SwingApp#exit()}.
 */
public class MainFrame extends JFrame {

    private String baseTitle;
    private final JMenuBar menuBar = new JMenuBar();
    private final JToolBar toolBar = new JToolBar();
    private final ViewManager views = new ViewManager();
    private final StatusBar statusBar = new StatusBar();
    private NavigationTree navigation;
    private final List<Shortcut> shortcuts = new ArrayList<>();

    public MainFrame(String title) {
        super(title);
        this.baseTitle = title;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setJMenuBar(menuBar);

        toolBar.setFloatable(false);
        toolBar.setVisible(false);

        getContentPane().add(toolBar, BorderLayout.NORTH);
        getContentPane().add(views.getComponent(), BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        views.currentView().onChange(view -> updateTitle());
    }

    /** Shows a navigation tree to the left of the views. */
    public void setNavigator(NavigationTree navigator) {
        this.navigation = navigator;
        getContentPane().remove(views.getComponent());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigator, views.getComponent());
        split.setDividerLocation(220);
        split.setBorder(BorderFactory.createEmptyBorder());
        getContentPane().add(split, BorderLayout.CENTER);
        revalidate();
    }

    public NavigationTree getNavigator() {
        return navigation;
    }

    /** Ctrl+K opens {@link QuickOpen} over the navigation tree's views and the menu commands. */
    public void enableQuickOpen() {
        bindKey("ctrl K", "shortcuts.quickOpen", () -> QuickOpen.show(this, () -> new CommandIndex(commands())));
    }

    /** Everything {@link QuickOpen} offers: views the user may open, then enabled menu items. */
    public List<CommandIndex.Command> commands() {
        List<CommandIndex.Command> commands = new ArrayList<>();
        if (navigation != null) {
            commands.addAll(CommandIndex.fromNavigation(navigation, views));
        }
        commands.addAll(CommandIndex.fromMenuBar(menuBar));
        return commands;
    }

    /** Runs the action when the key is pressed anywhere in the window, e.g. {@code bindKey("F1", ...)}. */
    public void bindKey(String keyStroke, Runnable action) {
        bindKey(keyStroke, null, action);
    }

    /**
     * Like {@link #bindKey(String, Runnable)}, and lists the key in the {@link ShortcutsDialog} with the
     * description (a translation key or plain text).
     */
    public void bindKey(String keyStroke, String description, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        if (stroke == null) {
            throw new IllegalArgumentException("Invalid key stroke: '" + keyStroke + "'");
        }
        String key = "rapidswing.key." + keyStroke;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, key);
        getRootPane().getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        shortcuts.removeIf(shortcut -> shortcut.getKeyStroke().equals(stroke));
        if (description != null) {
            shortcuts.add(new Shortcut(stroke, description));
        }
    }

    /** The keys bound with a description, in the order they were bound. */
    public List<Shortcut> shortcuts() {
        return new ArrayList<>(shortcuts);
    }

    /** The application name part of the window title, e.g. to translate it. */
    public void setBaseTitle(String title) {
        this.baseTitle = title;
        updateTitle();
    }

    /** "App - View", or just "App" when the view has no title of its own (or the app's name as its title). */
    private void updateTitle() {
        View view = views.currentView().get();
        String viewTitle = view == null ? null : view.getTitle();
        boolean ownTitle = viewTitle != null && !viewTitle.isEmpty() && !viewTitle.equals(baseTitle);
        setTitle(ownTitle ? baseTitle + " - " + viewTitle : baseTitle);
    }

    /** Returns the menu with this name, creating it at the end of the menu bar if needed. */
    public JMenu menu(String name) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && name.equals(menu.getText())) {
                return menu;
            }
        }
        JMenu menu = new JMenu(name);
        menuBar.add(menu);
        return menu;
    }

    /** Adds actions to the named menu. A {@code null} action adds a separator. */
    public MainFrame addMenu(String name, Action... actions) {
        JMenu menu = menu(name);
        for (Action action : actions) {
            if (action == null) {
                menu.addSeparator();
            } else {
                JMenuItem item = menu.add(action);
                hideWhenNotPermitted(item, action);
            }
        }
        return this;
    }

    /** Adds actions to the toolbar. A {@code null} action adds a separator. */
    public MainFrame addToolbar(Action... actions) {
        for (Action action : actions) {
            if (action == null) {
                toolBar.addSeparator();
            } else {
                JButton button = toolBar.add(action);
                button.setFocusable(false);
                hideWhenNotPermitted(button, action);
            }
        }
        toolBar.setVisible(toolBar.getComponentCount() > 0);
        return this;
    }

    private static void hideWhenNotPermitted(Component component, Action action) {
        if (action instanceof AppAction) {
            AppAction appAction = (AppAction) action;
            component.setVisible(Boolean.TRUE.equals(appAction.permitted().get()));
            appAction.permitted().onChange(allowed -> component.setVisible(Boolean.TRUE.equals(allowed)));
        }
    }

    public ViewManager views() {
        return views;
    }

    public StatusBar statusBar() {
        return statusBar;
    }

    public JToolBar toolBar() {
        return toolBar;
    }
}
