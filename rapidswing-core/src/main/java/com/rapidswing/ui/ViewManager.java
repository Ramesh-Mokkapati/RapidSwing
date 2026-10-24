package com.rapidswing.ui;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Registers views by id and switches between them, with back navigation.
 * Views are created on first display and then reused. Views can be restricted, e.g. to user roles:
 * a restricted view is not created or shown while access is denied.
 */
public class ViewManager {

    private final CardLayout cards = new CardLayout();
    private final JPanel container = new JPanel(cards);
    private final Map<String, Supplier<? extends View>> factories = new LinkedHashMap<>();
    private final Map<String, View> views = new HashMap<>();
    private final Deque<String> history = new ArrayDeque<>();
    private final Map<String, Property<Boolean>> restrictions = new HashMap<>();
    private Consumer<String> accessDeniedHandler = id -> { };

    private final Property<String> currentId = new Property<>();
    private final Property<View> currentView = new Property<>();
    private final Property<Boolean> canGoBack = new Property<>(false);

    public ViewManager register(String id, Supplier<? extends View> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (factories.containsKey(id)) {
            throw new IllegalArgumentException("A view is already registered with id '" + id + "'");
        }
        factories.put(id, factory);
        return this;
    }

    /**
     * Allows showing the view only while the condition is {@code true}, e.g.
     * {@code views.restrict("admin", session.anyRole("admin"))}.
     */
    public ViewManager restrict(String id, Property<Boolean> allowed) {
        restrictions.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(allowed, "allowed"));
        return this;
    }

    public boolean isRegistered(String id) {
        return factories.containsKey(id);
    }

    /** The ids of all registered views, in registration order. */
    public List<String> registeredIds() {
        return new ArrayList<>(factories.keySet());
    }

    /** The condition passed to {@link #restrict}, or {@code null} if the view is not restricted. */
    public Property<Boolean> restriction(String id) {
        return restrictions.get(id);
    }

    /**
     * Creates a new, separate instance of a view, e.g. to show it in its own window, without showing it here.
     * Returns {@code null} if access is denied. Factories that always return the same instance cannot be used
     * this way: {@link DetachedWindow} checks for that.
     */
    public View create(String id) {
        Edt.checkEdt();
        Supplier<? extends View> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No view registered with id '" + id + "'");
        }
        if (!isAllowed(id)) {
            accessDeniedHandler.accept(id);
            return null;
        }
        return Objects.requireNonNull(factory.get(), "View factory returned null for '" + id + "'");
    }

    public boolean isAllowed(String id) {
        Property<Boolean> allowed = restrictions.get(id);
        return allowed == null || Boolean.TRUE.equals(allowed.get());
    }

    /** Called with the view id when navigation to a restricted view is refused. */
    public void onAccessDenied(Consumer<String> handler) {
        this.accessDeniedHandler = Objects.requireNonNull(handler, "handler");
    }

    /** Shows the view. Returns false if access is denied or the current view refused to be left. */
    public boolean show(String id) {
        return navigate(id, true);
    }

    /** Returns to the previous view. Returns false if there is none or navigation was refused. */
    public boolean back() {
        String previous = history.peek();
        if (previous == null || !navigate(previous, false)) {
            return false;
        }
        history.pop();
        canGoBack.set(!history.isEmpty());
        return true;
    }

    /**
     * Discards all created views and shows the current one again, freshly created by its factory.
     * Use it after a locale change so views are rebuilt with the new texts and formats.
     * It does not ask {@link View#canLeave()}: check that first if unsaved work matters.
     */
    public void reload() {
        Edt.checkEdt();
        String id = currentId.get();
        discardAll();
        if (id != null && isAllowed(id)) {
            navigate(id, false);
        }
    }

    /** Discards all views and the history and shows nothing, e.g. when the user signs out. */
    public void reset() {
        Edt.checkEdt();
        discardAll();
        history.clear();
        canGoBack.set(false);
    }

    private void discardAll() {
        View current = currentView.get();
        if (current != null) {
            current.onHide();
        }
        container.removeAll();
        views.clear();
        currentId.set(null);
        currentView.set(null);
        container.revalidate();
        container.repaint();
    }

    public JComponent getComponent() {
        return container;
    }

    public Property<String> currentId() {
        return currentId;
    }

    public Property<View> currentView() {
        return currentView;
    }

    public Property<Boolean> canGoBack() {
        return canGoBack;
    }

    private boolean navigate(String id, boolean recordHistory) {
        Edt.checkEdt();
        Supplier<? extends View> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No view registered with id '" + id + "'");
        }
        String fromId = currentId.get();
        if (id.equals(fromId)) {
            return true;
        }
        if (!isAllowed(id)) {
            accessDeniedHandler.accept(id);
            return false;
        }
        View from = currentView.get();
        if (from != null && !from.canLeave()) {
            return false;
        }

        View to = views.get(id);
        if (to == null) {
            to = Objects.requireNonNull(factory.get(), "View factory returned null for '" + id + "'");
            views.put(id, to);
            container.add(to.getComponent(), id);
        }

        if (from != null) {
            from.onHide();
        }
        cards.show(container, id);
        if (recordHistory && fromId != null) {
            history.push(fromId);
            canGoBack.set(true);
        }
        currentId.set(id);
        currentView.set(to);
        to.onShow();
        return true;
    }
}
