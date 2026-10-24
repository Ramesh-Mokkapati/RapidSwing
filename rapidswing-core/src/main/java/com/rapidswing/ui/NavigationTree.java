package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A navigation tree of folders and views. Selecting a view shows it; the tree follows
 * the current view; views the user may not open (see {@link ViewManager#restrict}) are hidden, and folders left
 * empty disappear with them.
 * <pre>
 * NavigationTree navigation = new NavigationTree(ctx.views());
 * navigation.root().folder("sales", "nav.sales").page("/help/sales.html")
 *         .view("customers", "nav.customers")
 *         .view("orders", "nav.orders");
 * frame.setNavigator(navigation);
 * </pre>
 * Titles may be translation keys: they are looked up in {@link I18n} and follow language changes.
 */
public class NavigationTree extends JPanel {

    /** A folder node; build the tree by chaining {@link #folder}, {@link #view} and {@link #end}. */
    public final class Folder {
        private final String id;
        private final String title;
        private final Folder parent;
        private final List<Object> children = new ArrayList<>();
        private String pageViewId;

        private Folder(String id, String title, Folder parent) {
            this.id = id;
            this.title = title;
            this.parent = parent;
        }

        /** Adds a sub-folder and returns it. */
        public Folder folder(String folderId, String folderTitle) {
            Folder child = new Folder(folderId, folderTitle, this);
            children.add(child);
            refresh();
            return child;
        }

        /** Adds a view (registered with the {@link ViewManager}) and returns this folder, for chaining. */
        public Folder view(String viewId, String viewTitle) {
            children.add(new ViewNode(viewId, viewTitle));
            refresh();
            return this;
        }

        /**
         * Shows an HTML page when the folder is selected. The page is a classpath
         * resource; links of the form {@code view:id} open views.
         */
        public Folder page(String htmlResource) {
            pageViewId = "folder:" + id;
            if (!views.isRegistered(pageViewId)) {
                views.register(pageViewId, () -> new HtmlView(text(title), htmlResource, views));
            }
            refresh();
            return this;
        }

        /** The parent folder, to continue building there. */
        public Folder end() {
            return parent != null ? parent : this;
        }
    }

    private static final class ViewNode {
        final String viewId;
        final String title;

        ViewNode(String viewId, String title) {
            this.viewId = viewId;
            this.title = title;
        }
    }

    /** What a tree node shows: its text, and the view it opens (if any). */
    static final class Entry {
        final String text;
        final String viewId;

        Entry(String text, String viewId) {
            this.text = text;
            this.viewId = viewId;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final ViewManager views;
    private final Folder root = new Folder("root", "", null);
    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(model);
    private final Set<String> watchedRestrictions = new HashSet<>();
    private boolean syncing;

    public NavigationTree(ViewManager views) {
        super(new BorderLayout());
        this.views = Objects.requireNonNull(views, "views");
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        tree.addTreeSelectionListener(e -> selectionChanged());
        views.currentId().onChange(id -> selectView(id));
        I18n.global().onChange(this::refresh);
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public Folder root() {
        return root;
    }

    public JTree getTree() {
        return tree;
    }

    /** Rebuilds the visible tree, e.g. after roles changed. Called automatically when restrictions change. */
    public void refresh() {
        Edt.run(() -> {
            DefaultMutableTreeNode top = new DefaultMutableTreeNode();
            for (Object child : root.children) {
                DefaultMutableTreeNode node = build(child);
                if (node != null) {
                    top.add(node);
                }
            }
            syncing = true;
            try {
                model.setRoot(top);
                for (int row = 0; row < tree.getRowCount(); row++) {
                    tree.expandRow(row);
                }
            } finally {
                syncing = false;
            }
            selectView(views.currentId().get());
        });
    }

    /** The view ids reachable in the tree right now, in tree order: what the user sees. */
    public List<String> visibleViewIds() {
        List<String> ids = new ArrayList<>();
        Enumeration<?> nodes = ((DefaultMutableTreeNode) model.getRoot()).preorderEnumeration();
        while (nodes.hasMoreElements()) {
            Object value = ((DefaultMutableTreeNode) nodes.nextElement()).getUserObject();
            if (value instanceof Entry && ((Entry) value).viewId != null) {
                ids.add(((Entry) value).viewId);
            }
        }
        return Collections.unmodifiableList(ids);
    }

    /** Titles with their folder path, e.g. "Sales › Customers", for {@link QuickOpen}. */
    List<String[]> viewPaths() {
        List<String[]> paths = new ArrayList<>();
        collectPaths(root, "", paths);
        return paths;
    }

    private void collectPaths(Folder folder, String prefix, List<String[]> paths) {
        for (Object child : folder.children) {
            if (child instanceof Folder) {
                Folder sub = (Folder) child;
                String path = prefix.isEmpty() ? text(sub.title) : prefix + " › " + text(sub.title);
                if (sub.pageViewId != null && views.isAllowed(sub.pageViewId)) {
                    paths.add(new String[]{sub.pageViewId, text(sub.title), prefix});
                }
                collectPaths(sub, path, paths);
            } else {
                ViewNode view = (ViewNode) child;
                if (views.isRegistered(view.viewId) && views.isAllowed(view.viewId)) {
                    paths.add(new String[]{view.viewId, text(view.title), prefix});
                }
            }
        }
    }

    private DefaultMutableTreeNode build(Object definition) {
        if (definition instanceof ViewNode) {
            ViewNode view = (ViewNode) definition;
            watch(view.viewId);
            if (!views.isRegistered(view.viewId) || !views.isAllowed(view.viewId)) {
                return null;
            }
            return new DefaultMutableTreeNode(new Entry(text(view.title), view.viewId), false);
        }
        Folder folder = (Folder) definition;
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Entry(text(folder.title), folder.pageViewId));
        for (Object child : folder.children) {
            DefaultMutableTreeNode childNode = build(child);
            if (childNode != null) {
                node.add(childNode);
            }
        }
        return node.getChildCount() == 0 && folder.pageViewId == null ? null : node;
    }

    /** Rebuilds the tree whenever a view's restriction changes, e.g. on sign-in as another user. */
    private void watch(String viewId) {
        Property<Boolean> restriction = views.restriction(viewId);
        if (restriction != null && watchedRestrictions.add(viewId)) {
            restriction.onChange(allowed -> refresh());
        }
    }

    private void selectionChanged() {
        if (syncing) {
            return;
        }
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(value instanceof Entry) || ((Entry) value).viewId == null) {
            return;
        }
        String viewId = ((Entry) value).viewId;
        if (!viewId.equals(views.currentId().get()) && !views.show(viewId)) {
            // Navigation was refused (unsaved changes or no access): point back at the current view.
            selectView(views.currentId().get());
        }
    }

    private void selectView(String viewId) {
        syncing = true;
        try {
            if (viewId == null) {
                tree.clearSelection();
                return;
            }
            Enumeration<?> nodes = ((DefaultMutableTreeNode) model.getRoot()).preorderEnumeration();
            while (nodes.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
                Object value = node.getUserObject();
                if (value instanceof Entry && viewId.equals(((Entry) value).viewId)) {
                    TreePath path = new TreePath(node.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    return;
                }
            }
            tree.clearSelection();
        } finally {
            syncing = false;
        }
    }

    /** A translation key's text, or the text itself if it is not a key. */
    static String text(String keyOrText) {
        I18n i18n = I18n.global();
        return keyOrText != null && i18n.has(keyOrText) ? i18n.get(keyOrText) : keyOrText;
    }
}
