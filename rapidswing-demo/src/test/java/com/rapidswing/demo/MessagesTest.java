package com.rapidswing.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessagesTest {

    @Test
    void everyTranslationHasExactlyTheBaseKeys() throws IOException {
        Properties base = load("/messages.properties");
        for (String language : Arrays.asList("de", "es")) {
            Properties translation = load("/messages_" + language + ".properties");
            assertEquals(base.stringPropertyNames(), translation.stringPropertyNames(), "Keys of " + language);
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
