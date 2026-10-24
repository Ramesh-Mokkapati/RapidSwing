package com.rapidswing.generator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateTest {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Boolean> flags = new HashMap<>();

    TemplateTest() {
        values.put("name", "Notes");
        flags.put("login", true);
        flags.put("i18n", false);
    }

    @Test
    void insertsValues() {
        assertEquals("class Notes {}", Template.render("class {{name}} {}", values, flags));
    }

    @Test
    void conditionalBlocksAndStandaloneTagLines() {
        String template = "a\n{{#if login}}\nb\n{{/if}}\n{{#unless login}}\nc\n{{/unless}}\nd {{#if i18n}}e{{/if}}f\n";

        assertEquals("a\nb\nd f\n", Template.render(template, values, flags));
    }

    @Test
    void nestedBlocks() {
        String template = "{{#if login}}x{{#if i18n}}y{{/if}}{{#unless i18n}}z{{/unless}}{{/if}}";

        assertEquals("xz", Template.render(template, values, flags));
    }

    @Test
    void typosAreErrors() {
        assertThrows(IllegalArgumentException.class, () -> Template.render("{{nmae}}", values, flags));
        assertThrows(IllegalArgumentException.class, () -> Template.render("{{#if logn}}x{{/if}}", values, flags));
        assertThrows(IllegalArgumentException.class, () -> Template.render("{{#if login}}x", values, flags));
    }

    @Test
    void replacementTextIsLiteral() {
        values.put("name", "$1 \\ cost");

        assertEquals("$1 \\ cost", Template.render("{{name}}", values, flags));
    }
}
