package com.rapidswing.binding;

import com.rapidswing.property.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared implementation for all bindings: validity state, a re-entrancy guard, and cleanup actions.
 */
final class BindingImpl implements Binding {

    private final Property<Boolean> valid = new Property<>(true);
    private final Property<String> error = new Property<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private Runnable refresher = () -> { };
    private boolean updating;

    /**
     * Runs the update unless another update is already in progress. This stops the
     * component-to-property-to-component echo that would otherwise loop forever.
     */
    void guarded(Runnable update) {
        if (updating) {
            return;
        }
        updating = true;
        try {
            update.run();
        } finally {
            updating = false;
        }
    }

    /** True while this binding is pushing a change itself, so the resulting events are its own echo. */
    boolean isUpdating() {
        return updating;
    }

    void onUnbind(Runnable cleanup) {
        cleanups.add(cleanup);
    }

    /** The property-to-component update, re-run by {@link #refresh()}. */
    void setRefresher(Runnable refresher) {
        this.refresher = refresher;
    }

    @Override
    public void refresh() {
        refresher.run();
    }

    void markValid() {
        error.set(null);
        valid.set(true);
    }

    void markInvalid(String message) {
        error.set(message);
        valid.set(false);
    }

    @Override
    public void unbind() {
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            cleanups.get(i).run();
        }
        cleanups.clear();
        refresher = () -> { };
        markValid();
    }

    @Override
    public Property<Boolean> valid() {
        return valid;
    }

    @Override
    public Property<String> error() {
        return error;
    }
}
