package com.rapidswing.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Window;
import java.util.Locale;

/**
 * Look and Feel management based on FlatLaf. FlatLaf also scales the UI on HiDPI screens,
 * which Java 8 does not do by itself on Windows.
 */
public final class Themes {

    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String INTELLIJ = "intellij";
    public static final String DARCULA = "darcula";
    public static final String SYSTEM = "system";

    private static final Logger log = LoggerFactory.getLogger(Themes.class);

    private static String current;

    private Themes() {
    }

    /** Sets the Look and Feel. Call it before creating any components. Unknown names fall back to {@link #LIGHT}. */
    public static void install(String name) {
        String theme = name == null ? LIGHT : name.trim().toLowerCase(Locale.ROOT);
        try {
            switch (theme) {
                case LIGHT:
                    UIManager.setLookAndFeel(new FlatLightLaf());
                    break;
                case DARK:
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                    break;
                case INTELLIJ:
                    UIManager.setLookAndFeel(new FlatIntelliJLaf());
                    break;
                case DARCULA:
                    UIManager.setLookAndFeel(new FlatDarculaLaf());
                    break;
                case SYSTEM:
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
                default:
                    log.warn("Unknown theme '{}', using '{}'", name, LIGHT);
                    UIManager.setLookAndFeel(new FlatLightLaf());
                    theme = LIGHT;
            }
            current = theme;
        } catch (Exception e) {
            log.error("Could not install theme '{}'", theme, e);
        }
    }

    /** Switches the Look and Feel at runtime and refreshes every open window. */
    public static void switchTo(String name) {
        install(name);
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }

    public static String current() {
        return current;
    }

    public static boolean isDark() {
        return UIManager.getLookAndFeel() instanceof FlatLaf && ((FlatLaf) UIManager.getLookAndFeel()).isDark();
    }
}
