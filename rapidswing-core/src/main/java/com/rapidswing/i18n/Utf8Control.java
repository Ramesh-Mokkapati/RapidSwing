package com.rapidswing.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Loads {@code .properties} bundles as UTF-8 (Java 8 reads them as ISO-8859-1, which garbles text like
 * "Größe" or "Español"), and never falls back to the operating system's locale: a missing translation
 * falls back to the base bundle, not to whatever language the computer happens to use.
 */
final class Utf8Control extends ResourceBundle.Control {

    static final Utf8Control INSTANCE = new Utf8Control();

    private Utf8Control() {
    }

    @Override
    public List<String> getFormats(String baseName) {
        return FORMAT_PROPERTIES;
    }

    @Override
    public Locale getFallbackLocale(String baseName, Locale locale) {
        return null;
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
                                    boolean reload) throws IOException {
        String resource = toResourceName(toBundleName(baseName, locale), "properties");
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new PropertyResourceBundle(reader);
        }
    }
}
