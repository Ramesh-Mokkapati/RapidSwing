package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

/** A key and what it does, as listed in the {@link ShortcutsDialog}. */
public final class Shortcut {

    private final KeyStroke keyStroke;
    private final String description;

    public Shortcut(KeyStroke keyStroke, String description) {
        this.keyStroke = Objects.requireNonNull(keyStroke, "keyStroke");
        this.description = Objects.requireNonNull(description, "description");
    }

    public KeyStroke getKeyStroke() {
        return keyStroke;
    }

    /** The description, translated if it is a translation key. */
    public String getDescription() {
        return I18n.global().has(description) ? I18n.global().get(description) : description;
    }

    /** The key as users read it, e.g. "Ctrl+Shift+K". */
    public String getKeyText() {
        return keyText(keyStroke);
    }

    public static String keyText(KeyStroke stroke) {
        String modifiers = InputEvent.getModifiersExText(stroke.getModifiers());
        String key = stroke.getKeyCode() == KeyEvent.VK_UNDEFINED ? String.valueOf(stroke.getKeyChar())
                : KeyEvent.getKeyText(stroke.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }
}
