package com.rapidswing.i18n;

import com.rapidswing.util.Subscription;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nTest {

    @Test
    void appBundleOverridesFrameworkBundle() {
        I18n i18n = new I18n(Locale.ENGLISH).addBundle("test-i18n");

        assertEquals("Custom required", i18n.get("validation.required"));
        assertEquals("Enter a valid email address", i18n.get("validation.email"));
        assertEquals("Hello, Asha", i18n.get("greeting", "Asha"));
    }

    @Test
    void readsBundlesAsUtf8() {
        I18n i18n = new I18n(Locale.GERMAN).addBundle("test-i18n");

        assertEquals("Größe", i18n.get("size"));
        assertEquals("Bestätigen", i18n.get("dialog.confirm"));
        // Keys missing in the German app bundle fall back to its base bundle.
        assertEquals("Custom required", i18n.get("validation.required"));
    }

    @Test
    void pluralsAndNumbersFollowTheLocale() {
        I18n english = new I18n(Locale.ENGLISH);
        assertEquals("0 rows", english.get("grid.rows", 0));
        assertEquals("1 row", english.get("grid.rows", 1));
        assertEquals("1,234 rows", english.get("grid.rows", 1234));
        assertEquals("3 of 12 rows", english.get("grid.rowsFiltered", 3, 12));

        I18n german = new I18n(Locale.GERMAN);
        assertEquals("1 Zeile", german.get("grid.rows", 1));
        assertEquals("1.234 Zeilen", german.get("grid.rows", 1234));
        assertEquals("¿Eliminar Asha?", new I18n(new Locale("es")).get("crud.confirmDelete", "Asha"));
    }

    @Test
    void missingKeysAreVisibleInTheUi() {
        I18n i18n = new I18n(Locale.ENGLISH);

        assertEquals("!no.such.key!", i18n.get("no.such.key"));
        assertFalse(i18n.has("no.such.key"));
        assertTrue(i18n.has("grid.search"));
    }

    @Test
    void unsupportedLocaleUsesBaseBundleNotTheSystemLanguage() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            I18n i18n = new I18n(Locale.FRENCH).addBundle("test-i18n");

            assertEquals("Size", i18n.get("size"));
            assertEquals("Search", i18n.get("grid.search"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void bindingsFollowLocaleChangesUntilUnsubscribed() {
        I18n i18n = new I18n(Locale.ENGLISH).addBundle("test-i18n");
        List<String> texts = new ArrayList<>();
        AtomicInteger changes = new AtomicInteger();
        Subscription binding = i18n.bind(texts::add, "greeting", "Mei");
        i18n.onChange(changes::incrementAndGet);

        i18n.setLocale(Locale.GERMAN);
        i18n.setLocale(Locale.GERMAN);
        binding.unsubscribe();
        i18n.setLocale(Locale.ENGLISH);

        assertEquals(Arrays.asList("Hello, Mei", "Hallo, Mei"), texts);
        assertEquals(2, changes.get());
        assertEquals(Locale.ENGLISH, i18n.getLocale());
    }

    @Test
    void everyFrameworkTranslationHasExactlyTheBaseKeys() throws IOException {
        Properties base = load("/com/rapidswing/i18n/rapidswing.properties");
        for (String language : Arrays.asList("de", "es")) {
            Properties translation = load("/com/rapidswing/i18n/rapidswing_" + language + ".properties");
            assertEquals(base.stringPropertyNames(), translation.stringPropertyNames(), "Keys of " + language);
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
