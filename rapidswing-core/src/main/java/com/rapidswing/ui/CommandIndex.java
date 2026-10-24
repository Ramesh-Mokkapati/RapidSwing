package com.rapidswing.ui;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The commands {@link QuickOpen} can find: views from a {@link NavigationTree} and items from the menu bar.
 * Matching: {@code *} is a wildcard, otherwise titles starting with the text come
 * first, then titles containing it. Case is ignored.
 */
public final class CommandIndex {

    /** Something the user can run: a view to open or a menu command. */
    public static final class Command {
        private final String title;
        private final String location;
        private final Runnable run;

        public Command(String title, String location, Runnable run) {
            this.title = Objects.requireNonNull(title, "title");
            this.location = location == null ? "" : location;
            this.run = Objects.requireNonNull(run, "run");
        }

        public String getTitle() {
            return title;
        }

        /** Where it lives, e.g. "Sales" for a view in that folder or "View" for a menu item. */
        public String getLocation() {
            return location;
        }

        public void run() {
            run.run();
        }

        @Override
        public String toString() {
            return location.isEmpty() ? title : title + "  —  " + location;
        }
    }

    private final List<Command> commands;

    public CommandIndex(List<Command> commands) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
    }

    public List<Command> all() {
        return commands;
    }

    /** Matching commands, best first. An empty query returns everything. */
    public List<Command> search(String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return commands;
        }
        List<Command> starts = new ArrayList<>();
        List<Command> contains = new ArrayList<>();
        if (text.contains("*")) {
            Pattern pattern = Pattern.compile(wildcard(text));
            for (Command command : commands) {
                if (pattern.matcher(command.getTitle().toLowerCase(Locale.ROOT)).matches()) {
                    starts.add(command);
                }
            }
            return starts;
        }
        for (Command command : commands) {
            String title = command.getTitle().toLowerCase(Locale.ROOT);
            if (title.startsWith(text)) {
                starts.add(command);
            } else if (title.contains(text) || command.getLocation().toLowerCase(Locale.ROOT).contains(text)) {
                contains.add(command);
            }
        }
        starts.addAll(contains);
        return starts;
    }

    /** Views of the navigation tree that the user may open. */
    public static List<Command> fromNavigation(NavigationTree navigation, ViewManager views) {
        List<Command> found = new ArrayList<>();
        for (String[] view : navigation.viewPaths()) {
            String viewId = view[0];
            found.add(new Command(view[1], view[2], () -> views.show(viewId)));
        }
        return found;
    }

    /** Enabled, visible menu items, with their menu as location. */
    public static List<Command> fromMenuBar(JMenuBar menuBar) {
        List<Command> found = new ArrayList<>();
        if (menuBar != null) {
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu != null && menu.isVisible()) {
                    collect(menu, menu.getText(), found);
                }
            }
        }
        return found;
    }

    private static void collect(JMenu menu, String location, List<Command> found) {
        for (Component child : menu.getMenuComponents()) {
            if (!(child instanceof JMenuItem) || !child.isVisible() || !child.isEnabled()) {
                continue;
            }
            JMenuItem item = (JMenuItem) child;
            if (item instanceof JMenu) {
                collect((JMenu) item, location + " › " + item.getText(), found);
            } else if (item.getText() != null && !item.getText().isEmpty()) {
                found.add(new Command(item.getText(), location, item::doClick));
            }
        }
    }

    private static String wildcard(String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : text.split("\\*", -1)) {
            regex.append(Pattern.quote(part)).append(".*");
        }
        return regex.substring(0, regex.length() - 2) + ".*";
    }
}
