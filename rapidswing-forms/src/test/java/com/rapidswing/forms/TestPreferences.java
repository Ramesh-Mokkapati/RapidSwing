package com.rapidswing.forms;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/**
 * {@link java.util.prefs.Preferences} kept in memory, so tests never read or change the real user settings
 * (window positions, chosen language, last user name).
 */
final class TestPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, TestPreferences> children = new HashMap<>();

    TestPreferences() {
        super(null, "");
    }

    private TestPreferences(TestPreferences parent, String name) {
        super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
        values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
        return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
        values.clear();
        children.clear();
    }

    @Override
    protected String[] keysSpi() {
        return values.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() {
        return children.keySet().toArray(new String[0]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return children.computeIfAbsent(name, child -> new TestPreferences(this, child));
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() {
    }
}
