package com.rapidswing.forms;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * Typed user settings that survive restarts: declare them once, read and observe
 * them as properties anywhere, and let users change them in a {@link SettingsDialog}, grouped by category.
 * Settings can be exported to and imported from a properties file, e.g. to move them to another computer.
 * <pre>
 * Settings settings = new Settings(ctx.preferences().node("settings"));
 * Property&lt;Integer&gt; pageSize = settings.category("Lists").integer("list.pageSize", "Rows per page", 50, 10, 500);
 * Property&lt;Boolean&gt; confirm = settings.category("General").bool("confirm.exit", "Ask before exiting", true);
 * </pre>
 * Changes are saved as soon as a property changes. Labels and categories may be translation keys. Use it on
 * the EDT.
 */
public class Settings {

    /** One declared setting. */
    static final class Setting<T> {
        final String key;
        final String label;
        final String category;
        final Class<T> type;
        final T defaultValue;
        final Property<T> property;
        final Function<String, T> parse;
        final int min;
        final int max;
        final T[] choices;

        Setting(String key, String label, String category, Class<T> type, T defaultValue, Function<String, T> parse,
                int min, int max, T[] choices) {
            this.key = key;
            this.label = label;
            this.category = category;
            this.type = type;
            this.defaultValue = defaultValue;
            this.parse = parse;
            this.min = min;
            this.max = max;
            this.choices = choices;
            this.property = new Property<>(defaultValue);
        }

        String text(Object value) {
            return value == null ? "" : value instanceof Enum ? ((Enum<?>) value).name() : value.toString();
        }

        /** The value stored as this text, or {@code null} if the text is not a valid value. */
        T parseValid(String stored) {
            try {
                T value = parse.apply(stored.trim());
                return accepts(value) ? value : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        /** Reads stored text, falling back to the default for missing or unusable values. */
        T read(String stored) {
            if (stored == null) {
                return defaultValue;
            }
            try {
                T value = parse.apply(stored);
                return accepts(value) ? value : defaultValue;
            } catch (RuntimeException e) {
                return defaultValue;
            }
        }

        boolean accepts(Object value) {
            if (value == null || !type.isInstance(value)) {
                return false;
            }
            if (value instanceof Integer) {
                int number = (Integer) value;
                return number >= min && number <= max;
            }
            if (choices != null) {
                for (T choice : choices) {
                    if (choice.equals(value)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }

    private final Preferences store;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private String category = "settings.general";

    public Settings(Preferences store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The category (tab) for the settings declared next. */
    public Settings category(String name) {
        this.category = Objects.requireNonNull(name, "name");
        return this;
    }

    public Property<String> text(String key, String label, String defaultValue) {
        return declare(new Setting<>(key, label, category, String.class, defaultValue, text -> text,
                0, 0, null));
    }

    public Property<Integer> integer(String key, String label, int defaultValue, int min, int max) {
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(key + ": default " + defaultValue + " is outside " + min + ".." + max);
        }
        return declare(new Setting<>(key, label, category, Integer.class, defaultValue, Integer::valueOf,
                min, max, null));
    }

    public Property<Boolean> bool(String key, String label, boolean defaultValue) {
        return declare(new Setting<>(key, label, category, Boolean.class, defaultValue, text -> {
            if (!text.equals("true") && !text.equals("false")) {
                throw new IllegalArgumentException(text);
            }
            return Boolean.valueOf(text);
        }, 0, 0, null));
    }

    /** One of the enum's constants, chosen from a combo box. */
    public <E extends Enum<E>> Property<E> choice(String key, String label, E defaultValue) {
        @SuppressWarnings("unchecked")
        Class<E> type = (Class<E>) defaultValue.getDeclaringClass();
        return declare(new Setting<>(key, label, category, type, defaultValue, text -> Enum.valueOf(type, text),
                0, 0, type.getEnumConstants()));
    }

    /** The property of a declared setting. */
    @SuppressWarnings("unchecked")
    public <T> Property<T> get(String key) {
        return (Property<T>) setting(key).property;
    }

    /** All settings back to their defaults. */
    public void reset() {
        for (Setting<?> setting : settings.values()) {
            resetOne(setting);
        }
    }

    /** Writes all settings as a properties file. */
    public void exportTo(OutputStream out) throws IOException {
        Properties properties = new Properties();
        for (Setting<?> setting : settings.values()) {
            properties.setProperty(setting.key, textOf(setting));
        }
        properties.store(out, "RapidSwing settings");
    }

    /**
     * Reads settings from a properties file. Unknown keys and invalid values are skipped, so a file from an older
     * or newer version can be imported. Returns how many settings were applied.
     */
    public int importFrom(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        int applied = 0;
        for (Setting<?> setting : settings.values()) {
            String text = properties.getProperty(setting.key);
            if (text != null && applyText(setting, text)) {
                applied++;
            }
        }
        return applied;
    }

    /** The categories in declaration order. */
    List<String> categories() {
        List<String> names = new ArrayList<>();
        for (Setting<?> setting : settings.values()) {
            if (!names.contains(setting.category)) {
                names.add(setting.category);
            }
        }
        return names;
    }

    List<Setting<?>> settingsIn(String categoryName) {
        List<Setting<?>> found = new ArrayList<>();
        for (Setting<?> setting : settings.values()) {
            if (setting.category.equals(categoryName)) {
                found.add(setting);
            }
        }
        return Collections.unmodifiableList(found);
    }

    static String text(String keyOrText) {
        return I18n.global().has(keyOrText) ? I18n.global().get(keyOrText) : keyOrText;
    }

    private <T> Property<T> declare(Setting<T> setting) {
        if (settings.containsKey(setting.key)) {
            throw new IllegalArgumentException("Setting '" + setting.key + "' is declared twice");
        }
        settings.put(setting.key, setting);
        setting.property.set(setting.read(store.get(setting.key, null)));
        setting.property.onChange(value -> {
            if (setting.accepts(value)) {
                store.put(setting.key, setting.text(value));
            } else {
                setting.property.set(setting.read(store.get(setting.key, null)));
            }
        });
        return setting.property;
    }

    private Setting<?> setting(String key) {
        Setting<?> setting = settings.get(key);
        if (setting == null) {
            throw new IllegalArgumentException("No setting '" + key + "'");
        }
        return setting;
    }

    private static <T> String textOf(Setting<T> setting) {
        return setting.text(setting.property.get());
    }

    private static <T> void resetOne(Setting<T> setting) {
        setting.property.set(setting.defaultValue);
    }

    private static <T> boolean applyText(Setting<T> setting, String text) {
        T value = setting.parseValid(text);
        if (value == null) {
            return false;
        }
        setting.property.set(value);
        return true;
    }
}
