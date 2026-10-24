package com.rapidswing.ui;

import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.prefs.Preferences;

/**
 * Remembers window size, position and maximized state between runs, using {@link Preferences}.
 */
public class WindowStateStore {

    private final Preferences preferences;

    public WindowStateStore(Preferences preferences) {
        this.preferences = preferences;
    }

    public void save(String key, Frame frame) {
        boolean maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        preferences.putBoolean(key + ".maximized", maximized);
        // Keep the last normal bounds while maximized, so restoring un-maximizes to a sensible size.
        if (!maximized) {
            Rectangle bounds = frame.getBounds();
            preferences.putInt(key + ".x", bounds.x);
            preferences.putInt(key + ".y", bounds.y);
            preferences.putInt(key + ".width", bounds.width);
            preferences.putInt(key + ".height", bounds.height);
        }
    }

    /** Returns false if nothing was saved or the saved bounds are no longer on any screen. */
    public boolean restore(String key, Frame frame) {
        int width = preferences.getInt(key + ".width", -1);
        int height = preferences.getInt(key + ".height", -1);
        if (width <= 0 || height <= 0) {
            return false;
        }
        Rectangle bounds = new Rectangle(preferences.getInt(key + ".x", 0), preferences.getInt(key + ".y", 0),
                width, height);
        if (!isOnScreen(bounds)) {
            return false;
        }
        frame.setBounds(bounds);
        if (preferences.getBoolean(key + ".maximized", false)) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        return true;
    }

    private static boolean isOnScreen(Rectangle bounds) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }
}
