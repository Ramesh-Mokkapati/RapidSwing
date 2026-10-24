package com.rapidswing.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void loadsResourceAndArgsOverrideIt() {
        AppConfig config = new AppConfig()
                .loadResource("/test-app.properties")
                .applyArgs("--app.theme=dark", "--debug", "positional", "--empty=");

        assertEquals("Test App", config.get("app.title", null));
        assertEquals("dark", config.get("app.theme", null));
        assertTrue(config.getBoolean("debug", false));
        assertEquals("", config.get("empty", null));
        assertFalse(config.find("positional").isPresent());
    }

    @Test
    void missingResourceIsIgnored() {
        AppConfig config = new AppConfig().loadResource("/does-not-exist.properties");

        assertEquals("fallback", config.get("anything", "fallback"));
    }

    @Test
    void typedGetters() {
        AppConfig config = new AppConfig().set("width", " 800 ").set("bad", "abc");

        assertEquals(800, config.getInt("width", 0));
        assertEquals(42, config.getInt("missing", 42));
        assertThrows(IllegalArgumentException.class, () -> config.getInt("bad", 0));
    }
}
