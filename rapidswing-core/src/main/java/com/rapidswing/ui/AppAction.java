package com.rapidswing.ui;

import com.rapidswing.property.Property;
import com.rapidswing.security.Session;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A Swing {@link javax.swing.Action} with a fluent API. One action can back a menu item, a toolbar button
 * and a keyboard shortcut, and they all stay in sync.
 * <pre>
 * AppAction save = AppAction.of("Save", this::save)
 *         .shortcut("ctrl S")
 *         .tooltip("Save changes")
 *         .enabledWhen(form.dirty())
 *         .requiresRole(session, "editor");
 * </pre>
 * An action that is not permitted is disabled, and {@link MainFrame} hides its menu item and toolbar button.
 */
public class AppAction extends AbstractAction {

    private final transient Consumer<ActionEvent> handler;
    private final transient Property<Boolean> permitted = new Property<>(true);
    private boolean requestedEnabled = true;

    public AppAction(String name, Consumer<ActionEvent> handler) {
        super(name);
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public static AppAction of(String name, Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        return new AppAction(name, event -> handler.run());
    }

    /** Keyboard shortcut in {@link KeyStroke#getKeyStroke(String)} format, e.g. {@code "ctrl S"} or {@code "alt LEFT"}. */
    public AppAction shortcut(String keyStroke) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        if (stroke == null) {
            throw new IllegalArgumentException("Invalid key stroke: '" + keyStroke + "'");
        }
        putValue(ACCELERATOR_KEY, stroke);
        return this;
    }

    public AppAction mnemonic(char key) {
        putValue(MNEMONIC_KEY, KeyEvent.getExtendedKeyCodeForChar(key));
        return this;
    }

    public AppAction tooltip(String text) {
        putValue(SHORT_DESCRIPTION, text);
        return this;
    }

    public AppAction icon(Icon icon) {
        putValue(SMALL_ICON, icon);
        putValue(LARGE_ICON_KEY, icon);
        return this;
    }

    public AppAction enabled(boolean enabled) {
        setEnabled(enabled);
        return this;
    }

    /** Keeps the action enabled only while the condition is {@code true}. */
    public AppAction enabledWhen(Property<Boolean> condition) {
        setEnabled(Boolean.TRUE.equals(condition.get()));
        condition.onChange(value -> setEnabled(Boolean.TRUE.equals(value)));
        return this;
    }

    /** Allows the action only while the permission is {@code true}, independently of {@link #setEnabled}. */
    public AppAction requires(Property<Boolean> permission) {
        applyPermission(permission.get());
        permission.onChange(this::applyPermission);
        return this;
    }

    /** Allows the action only while the signed-in user has one of the roles. */
    public AppAction requiresRole(Session session, String... roles) {
        return requires(session.anyRole(roles));
    }

    /** Whether the current user may use this action; menus and toolbars hide it while false. */
    public Property<Boolean> permitted() {
        return permitted;
    }

    /** The action is enabled only if it is both enabled here and permitted. */
    @Override
    public void setEnabled(boolean enabled) {
        requestedEnabled = enabled;
        super.setEnabled(enabled && Boolean.TRUE.equals(permitted.get()));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (Boolean.TRUE.equals(permitted.get())) {
            handler.accept(event);
        }
    }

    private void applyPermission(Boolean allowed) {
        permitted.set(Boolean.TRUE.equals(allowed));
        super.setEnabled(requestedEnabled && Boolean.TRUE.equals(allowed));
    }
}
