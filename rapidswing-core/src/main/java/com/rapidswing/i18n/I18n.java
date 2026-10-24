package com.rapidswing.i18n;

import com.rapidswing.property.Property;
import com.rapidswing.util.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Translated texts from resource bundles, with runtime locale switching.
 * <pre>
 * I18n i18n = I18n.global().addBundle("messages");     // messages.properties, messages_de.properties, ...
 * String title = i18n.get("customers.title");
 * String count = i18n.get("customers.count", 12);      // MessageFormat: "{0} customers"
 * i18n.bind(label::setText, "customers.title");         // updates when the locale changes
 * </pre>
 * Bundles are read as UTF-8. App bundles take precedence over the framework's own bundle, so an app can
 * override any framework text, e.g. {@code validation.required}. Missing keys show as {@code !key!}.
 * <p>
 * Change the locale on the EDT. {@link #get} may be called from any thread.
 */
public class I18n {

    /** The framework's own texts: validation messages, grid and CRUD labels, dialog titles. */
    public static final String FRAMEWORK_BUNDLE = "com.rapidswing.i18n.rapidswing";

    private static final Logger log = LoggerFactory.getLogger(I18n.class);
    private static final I18n GLOBAL = new I18n();

    private final List<String> baseNames = new CopyOnWriteArrayList<>();
    private final Property<Locale> locale;
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private volatile List<ResourceBundle> bundles = Collections.emptyList();

    /** Starts with the system's default locale. */
    public I18n() {
        this(Locale.getDefault());
    }

    public I18n(Locale initialLocale) {
        this.locale = new Property<>(Objects.requireNonNull(initialLocale, "initialLocale"));
        baseNames.add(FRAMEWORK_BUNDLE);
        bundles = load(initialLocale);
    }

    /** The instance used by the framework's components and by {@code AppContext.i18n()}. */
    public static I18n global() {
        return GLOBAL;
    }

    /** Adds an app bundle by base name, e.g. {@code "messages"} or {@code "com.example.app.messages"}. */
    public I18n addBundle(String baseName) {
        Objects.requireNonNull(baseName, "baseName");
        if (!baseNames.contains(baseName)) {
            baseNames.add(0, baseName);
            bundles = load(locale.get());
        }
        return this;
    }

    public Locale getLocale() {
        return locale.get();
    }

    /** Observe locale changes here; change the locale with {@link #setLocale}. */
    public Property<Locale> locale() {
        return locale;
    }

    /** Switches language. Bound texts and {@link #onChange} listeners update immediately. */
    public void setLocale(Locale newLocale) {
        Objects.requireNonNull(newLocale, "newLocale");
        if (newLocale.equals(locale.get())) {
            return;
        }
        bundles = load(newLocale);
        reportedMissing.clear();
        locale.set(newLocale);
    }

    /**
     * The text for a key. With arguments, the text is a {@link MessageFormat} pattern such as
     * {@code "Delete {0}?"}; in such patterns, write a literal apostrophe as {@code ''}.
     */
    public String get(String key, Object... args) {
        String pattern = find(key);
        if (pattern == null) {
            if (reportedMissing.add(key)) {
                log.warn("Missing translation for '{}' in locale '{}'", key, locale.get());
            }
            return "!" + key + "!";
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale.get()).format(args);
    }

    public boolean has(String key) {
        return find(key) != null;
    }

    /** Passes the text to the target now and again whenever the locale changes. */
    public Subscription bind(Consumer<String> target, String key, Object... args) {
        Objects.requireNonNull(target, "target");
        target.accept(get(key, args));
        return locale.onChange(newLocale -> target.accept(get(key, args)));
    }

    /** Keeps an action's name (menu item and button text) translated. */
    public Subscription bindAction(Action action, String key, Object... args) {
        return bind(text -> action.putValue(Action.NAME, text), key, args);
    }

    /** Runs the listener after every locale change, e.g. to refresh texts built from several keys. */
    public Subscription onChange(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        return locale.onChange(newLocale -> listener.run());
    }

    private String find(String key) {
        for (ResourceBundle bundle : bundles) {
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        }
        return null;
    }

    private List<ResourceBundle> load(Locale target) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = I18n.class.getClassLoader();
        }
        List<ResourceBundle> loaded = new ArrayList<>();
        for (String baseName : baseNames) {
            loaded.add(ResourceBundle.getBundle(baseName, target, loader, Utf8Control.INSTANCE));
        }
        return Collections.unmodifiableList(loaded);
    }
}
