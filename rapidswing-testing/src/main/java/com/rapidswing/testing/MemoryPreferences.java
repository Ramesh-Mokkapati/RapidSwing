package com.rapidswing.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/**
 * {@link java.util.prefs.Preferences} kept in memory, so tests never read or change the real user settings
 * (window positions, chosen language, last user name).
 */
public final class MemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, MemoryPreferences> children = new HashMap<>();

    public MemoryPreferences() {
        super(null, "");
    }

    private MemoryPreferences(MemoryPreferences parent, String name) {
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
        return children.computeIfAbsent(name, child -> new MemoryPreferences(this, child));
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() {
    }
}
