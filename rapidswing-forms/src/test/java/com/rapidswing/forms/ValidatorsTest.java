package com.rapidswing.forms;

import com.rapidswing.i18n.I18n;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValidatorsTest {

    @Test
    void requiredRejectsEmptyValues() {
        Validator<Object> required = Validators.required();

        assertNotNull(required.validate(null));
        assertNotNull(required.validate("   "));
        assertNotNull(required.validate(Collections.emptyList()));
        assertNull(required.validate("x"));
        assertNull(required.validate(0));
        assertEquals("Name please", Validators.required("Name please").validate(""));
    }

    @Test
    void otherRulesAcceptEmptyValues() {
        assertNull(Validators.minLength(3).validate(""));
        assertNull(Validators.email().validate(null));
        assertNull(Validators.<Integer>range(1, 5).validate(null));
        assertNull(Validators.past().validate(null));
        assertNull(Validators.<String>of(s -> false, "never").validate(null));
    }

    @Test
    void lengthRules() {
        assertEquals("Must be at least 3 characters", Validators.minLength(3).validate("ab"));
        assertNull(Validators.minLength(3).validate("abc"));
        assertEquals("Must be at most 3 characters", Validators.maxLength(3).validate("abcd"));
    }

    @Test
    void emailAndPattern() {
        assertNull(Validators.email().validate("asha@example.com"));
        assertNotNull(Validators.email().validate("asha@example"));
        assertNotNull(Validators.email().validate("asha example@x.com"));
        assertEquals("Digits only", Validators.pattern("\\d+", "Digits only").validate("12a"));
    }

    @Test
    void comparisonRules() {
        assertEquals("Must be between 18 and 120", Validators.range(18, 120).validate(17));
        assertNull(Validators.range(18, 120).validate(18));
        assertNotNull(Validators.min(10).validate(9));
        assertNotNull(Validators.max(10).validate(11));
        assertNotNull(Validators.positive().validate(0));
        assertNull(Validators.positive().validate(0.5));
    }

    @Test
    void dateRules() {
        LocalDate today = LocalDate.now();
        assertNull(Validators.past().validate(today.minusDays(1)));
        assertNotNull(Validators.past().validate(today));
        assertNull(Validators.future().validate(today.plusDays(1)));
        assertNotNull(Validators.future().validate(today));
    }

    @Test
    void messagesFollowTheGlobalLocale() {
        I18n i18n = I18n.global();
        Locale saved = i18n.getLocale();
        Validator<CharSequence> rule = Validators.minLength(3);
        try {
            i18n.setLocale(Locale.GERMAN);
            assertEquals("Mindestens 3 Zeichen", rule.validate("ab"));
            assertEquals("Dieses Feld ist erforderlich", Validators.required().validate(""));
        } finally {
            i18n.setLocale(saved);
        }
        assertEquals("Must be at least 3 characters", rule.validate("ab"));
    }

    @Test
    void andRunsRulesInOrder() {
        Validator<CharSequence> rule = Validators.minLength(2).and(Validators.pattern("[a-z]+", "Lowercase only"));

        assertEquals("Must be at least 2 characters", rule.validate("A"));
        assertEquals("Lowercase only", rule.validate("AB"));
        assertNull(rule.validate("ab"));
    }
}
